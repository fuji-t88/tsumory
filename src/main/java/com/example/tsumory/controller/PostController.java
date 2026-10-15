package com.example.tsumory.controller;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.form.PostEditForm;
import com.example.tsumory.form.PostForm;
import com.example.tsumory.service.DailyPostLimitExceededException;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.PostService;
import com.example.tsumory.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
    private static final String DAILY_LIMIT_MESSAGE = "今日の投稿は " + PostService.DAILY_POST_LIMIT + " 件に達しました";

    private final PostService postService;
    private final DiaryService diaryService;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("postForm", new PostForm(null));
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
            postService.create(userId, postForm.content());
        } catch (DailyPostLimitExceededException e) {
            // ログは PostService が INFO で出しているので、ここでは出さない
            flash.addFlashAttribute("message", DAILY_LIMIT_MESSAGE);
        }
        return "redirect:/";
    }

    @GetMapping("/posts/{id}/edit")
    public String editMode(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            var post = postService.getEditablePost(principal.userId(), id);
            model.addAttribute("postForm", new PostForm(null));
            model.addAttribute("postEditForm", new PostEditForm(post.content(), post.category()));
            populateHomeModel(principal.userId(), model, PostView.from(post));
            return "home";
        } catch (ResourceNotFoundException e) {
            return redirectWithNotFound(redirectAttributes);
        }
    }

    @PostMapping("/posts/{id}/edit")
    public String edit(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @ModelAttribute("postEditForm") PostEditForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            if (bindingResult.hasErrors()) {
                // 入力エラーでも、編集できないつぶやきなら再表示せずに戻す
                var post = postService.getEditablePost(principal.userId(), id);
                model.addAttribute("postForm", new PostForm(null));
                populateHomeModel(principal.userId(), model, PostView.from(post));
                return "home";
            }
            postService.edit(principal.userId(), id, form.content(), form.category());
            return "redirect:/";
        } catch (ResourceNotFoundException e) {
            return redirectWithNotFound(redirectAttributes);
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
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        try {
            postService.delete(principal.userId(), id);
            return "redirect:/";
        } catch (ResourceNotFoundException e) {
            return redirectWithNotFound(redirectAttributes);
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
        model.addAttribute("postLimitReached", posts.size() >= PostService.DAILY_POST_LIMIT);
        model.addAttribute(
                "diary", diaryService.findDiaryForSummary(userId, today).orElse(null));
        model.addAttribute("editingPost", editingPost);
        // ホーム描画の全経路（通常表示・入力エラーの再表示）がカテゴリの選択肢を受け取れるよう、共有ヘルパーに置く
        model.addAttribute("categories", PostCategory.values());
    }

    // ログは PostService が WARN で出しているので、ここでは出さない
    private static String redirectWithNotFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("message", POST_NOT_FOUND_MESSAGE);
        return "redirect:/";
    }
}
