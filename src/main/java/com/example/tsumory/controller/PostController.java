package com.example.tsumory.controller;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.form.PostEditForm;
import com.example.tsumory.form.PostForm;
import com.example.tsumory.service.DailyPostLimitExceededException;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.InvalidPhotoException;
import com.example.tsumory.service.PhotoType;
import com.example.tsumory.service.PhotoUpload;
import com.example.tsumory.service.PhotoValidator;
import com.example.tsumory.service.PostService;
import com.example.tsumory.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PostController {

    /** 自分の今日か昨日のつぶやきでないもの（他人のもの、削除済み、一昨日より前）を指定されたときのメッセージ。 */
    static final String POST_NOT_FOUND_MESSAGE = "つぶやきが見つかりませんでした";

    /** 今日のつぶやきが上限に達しているのに投稿されたとき（別のタブから送った場合など）のメッセージ。 */
    private static final String DAILY_LIMIT_MESSAGE = "今日のつぶやきは " + PostService.DAILY_POST_LIMIT + " 件に達しました";

    private final PostService postService;
    private final DiaryService diaryService;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("postForm", PostForm.empty());
        populateHomeModel(principal.userId(), model, null);
        return "home";
    }

    @PostMapping("/posts")
    public String create(
            @Valid @ModelAttribute("postForm") PostForm postForm,
            BindingResult binding,
            @AuthenticationPrincipal CustomUserDetails principal,
            Model model,
            RedirectAttributes flash) {
        var userId = principal.userId();
        if (binding.hasErrors()) {
            populateHomeModel(userId, model, null);
            return "home";
        }
        try {
            postService.create(userId, postForm.content(), toPhotoUpload(postForm));
        } catch (DailyPostLimitExceededException e) {
            // ログは PostService が WARN で出しているので、ここでは出さない
            flash.addFlashAttribute("message", DAILY_LIMIT_MESSAGE);
        } catch (InvalidPhotoException e) {
            // 本文は残して、写真の欄にエラーを出す（ログは PostService が WARN で出している）
            binding.rejectValue("attachedPhotos", "invalidPhoto", photoErrorMessage(e.getReason()));
            populateHomeModel(userId, model, null);
            return "home";
        }
        return "redirect:/";
    }

    @GetMapping("/posts/{id}/edit")
    public String editMode(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            Model model,
            RedirectAttributes flash) {
        try {
            var post = postService.getEditablePost(principal.userId(), id);
            model.addAttribute("postForm", PostForm.empty());
            model.addAttribute("postEditForm", new PostEditForm(post.content(), post.category()));
            populateHomeModel(principal.userId(), model, PostView.from(post));
            return "home";
        } catch (ResourceNotFoundException e) {
            return redirectWithNotFound(flash);
        }
    }

    @PostMapping("/posts/{id}/edit")
    public String edit(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @ModelAttribute("postEditForm") PostEditForm form,
            BindingResult binding,
            Model model,
            RedirectAttributes flash) {
        try {
            if (binding.hasErrors()) {
                // 入力エラーでも、編集できないつぶやきなら再表示せずに戻す
                var post = postService.getEditablePost(principal.userId(), id);
                model.addAttribute("postForm", PostForm.empty());
                populateHomeModel(principal.userId(), model, PostView.from(post));
                return "home";
            }
            postService.edit(principal.userId(), id, form.content(), form.category());
            return "redirect:/";
        } catch (ResourceNotFoundException e) {
            return redirectWithNotFound(flash);
        }
    }

    /**
     * 自分のつぶやきに添付した写真を返す（phase3-spec.md の「6. 処理の詳細 > 写真の取得」）。形式は保存したときにデータから決めたもの。ほかの
     * ユーザーのつぶやき・写真のないつぶやき・削除したつぶやきは、どれも区別せずに 404（本文なし。画像の要求なのでエラー画面は返さない）。
     */
    @GetMapping("/posts/{id}/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        try {
            var photo = postService.findPhoto(principal.userId(), id);
            var type = MediaType.parseMediaType(photo.contentType());
            var disposition = ContentDisposition.inline()
                    .filename("photo." + type.getSubtype())
                    .build();
            return ResponseEntity.ok()
                    .contentType(type)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(photo.data());
        } catch (ResourceNotFoundException e) {
            // ログは PostService が WARN で出しているので、ここでは出さない
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * カテゴリだけを直す。{@code "WORK"} などの文字列は、Spring MVC が {@link PostCategory} に変換して渡す。
     *
     * <p>候補にない値（画面を改ざんされた場合など）は変換できず、{@link NotFoundExceptionHandler} が 404 にする。値がなければ 400。
     *
     * <p>日記表示から直したときは {@code date} にその画面の日付が入っていて、直したあと同じ {@code /diary/{date}} に戻る。{@code date} がなければ
     * {@code /}（今日のつぶやき画面）に戻る。戻り先は {@code LocalDate} に変換した値から組み立てるので、任意の URL へは飛ばない（オープンリダイレクト
     * の対策）。日付の形式でなければ、{@link NotFoundExceptionHandler} が 404 にする。
     */
    @PostMapping("/posts/{id}/category")
    public String updateCategory(
            @PathVariable Long id,
            @RequestParam PostCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails principal,
            RedirectAttributes flash) {
        try {
            postService.updateCategory(principal.userId(), id, category);
        } catch (ResourceNotFoundException e) {
            flash.addFlashAttribute("message", POST_NOT_FOUND_MESSAGE);
            return "redirect:/";
        }
        return date == null ? "redirect:/" : "redirect:/diary/" + date;
    }

    @PostMapping("/posts/{id}/delete")
    public String delete(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id, RedirectAttributes flash) {
        try {
            postService.delete(principal.userId(), id);
            return "redirect:/";
        } catch (ResourceNotFoundException e) {
            return redirectWithNotFound(flash);
        }
    }

    /**
     * ホーム画面（通常モード・編集モード）の値を詰める。今日の日付・今日のつぶやき・投稿の上限と上限に達したか・
     * 今日の日記（なければ {@code null}）・編集中のつぶやき（編集モードでなければ {@code null}）・カテゴリの選択肢。
     */
    private void populateHomeModel(Long userId, Model model, PostView editingPost) {
        // 「今日」はここで 1 回だけ求める（途中で 0 時をまたいでもずれない）
        var today = postService.today();
        var posts = postService.findPosts(userId, today);
        model.addAttribute("today", today);
        model.addAttribute("posts", posts.stream().map(PostView::from).toList());
        model.addAttribute("postLimit", PostService.DAILY_POST_LIMIT);
        model.addAttribute("postLimitReached", postService.isPostLimitReached(userId, today));
        model.addAttribute(
                "diary", diaryService.findDiaryForSummary(userId, today).orElse(null));
        model.addAttribute("editingPost", editingPost);
        // ホーム描画の全経路（通常表示・入力エラーの再表示）がカテゴリの選択肢を受け取れるよう、共有ヘルパーに置く
        model.addAttribute("categories", PostCategory.values());
    }

    /**
     * 添付された写真を service に渡す形にする。なければ {@code null}。枚数はフォームの入力チェックで確かめてある。形式と中身は service が確かめる
     * （phase3-spec.md の「6. 処理の詳細 > つぶやきの投稿」）。
     */
    private static PhotoUpload toPhotoUpload(PostForm form) {
        var photos = form.getAttachedPhotos();
        if (photos.isEmpty()) {
            return null;
        }
        try {
            return new PhotoUpload(photos.getFirst().getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded photo", e);
        }
    }

    /** 写真を受け付けない理由ごとの、画面に出すメッセージ。上限の数字は定数から出す。 */
    static String photoErrorMessage(InvalidPhotoException.Reason reason) {
        return switch (reason) {
            case EMPTY -> "写真のファイルが空です";
            case TOO_LARGE -> "写真は " + PhotoValidator.MAX_BYTES / (1024 * 1024) + "MB 以内にしてください";
            case UNSUPPORTED_TYPE -> "写真は " + PhotoType.displayNames() + " にしてください";
            case TOO_MANY_PIXELS -> "写真の画素数が多すぎます（" + PhotoValidator.MAX_PIXELS / 10_000 + " 万画素まで）";
            case BROKEN -> "写真を読み込めませんでした。別の写真を選んでください";
        };
    }

    // ログは PostService が WARN で出しているので、ここでは出さない
    private static String redirectWithNotFound(RedirectAttributes flash) {
        flash.addFlashAttribute("message", POST_NOT_FOUND_MESSAGE);
        return "redirect:/";
    }
}
