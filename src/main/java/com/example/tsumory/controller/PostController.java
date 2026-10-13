package com.example.tsumory.controller;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.form.PostForm;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.PostItem;
import com.example.tsumory.service.PostService;
import com.example.tsumory.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PostController {

    /** 自分の今日か昨日のつぶやきでないもの（他人のもの、削除済み、一昨日より前）を指定されたときのメッセージ。 */
    static final String POST_NOT_FOUND_MESSAGE = "つぶやきが見つかりませんでした";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final PostService postService;
    private final DiaryService diaryService;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("postForm", new PostForm(null));
        populateHomeModel(principal.userId(), null, model);
        return "home";
    }

    @PostMapping("/posts")
    public String post(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute("postForm") PostForm form,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            populateHomeModel(principal.userId(), null, model);
            return "home";
        }
        postService.post(principal.userId(), form.content());
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
            model.addAttribute("postEditForm", new PostForm(post.content()));
            populateHomeModel(principal.userId(), toView(post), model);
            return "home";
        } catch (ResourceNotFoundException e) {
            return redirectWithNotFound(redirectAttributes);
        }
    }

    @PostMapping("/posts/{id}/edit")
    public String edit(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @ModelAttribute("postEditForm") PostForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            if (bindingResult.hasErrors()) {
                // 入力エラーでも、編集できないつぶやきなら再表示せずに戻す
                var post = postService.getEditablePost(principal.userId(), id);
                model.addAttribute("postForm", new PostForm(null));
                populateHomeModel(principal.userId(), toView(post), model);
                return "home";
            }
            postService.edit(principal.userId(), id, form.content());
            return "redirect:/";
        } catch (ResourceNotFoundException e) {
            return redirectWithNotFound(redirectAttributes);
        }
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
     * ホーム画面（通常モード・編集モード）の値を詰める。今日の日付・今日のつぶやき・今日の日記（なければ {@code null}）・
     * 編集中のつぶやき（編集モードでなければ {@code null}）。
     */
    private void populateHomeModel(Long userId, PostView editingPost, Model model) {
        // 「今日」はここで 1 回だけ求める（途中で 0 時をまたいでもずれない）
        var today = postService.today();
        model.addAttribute("today", today);
        model.addAttribute(
                "posts",
                postService.findPosts(userId, today).stream()
                        .map(PostController::toView)
                        .toList());
        model.addAttribute(
                "diary", diaryService.findDiaryForSummary(userId, today).orElse(null));
        model.addAttribute("editingPost", editingPost);
    }

    /** 投稿時刻を表示用の文字列にして、画面専用の {@link PostView} に詰める。 */
    private static PostView toView(PostItem post) {
        return new PostView(post.id(), post.content(), post.postedTime().format(TIME_FORMAT), post.edited());
    }

    // ログは PostService が WARN で出しているので、ここでは出さない
    private static String redirectWithNotFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("message", POST_NOT_FOUND_MESSAGE);
        return "redirect:/";
    }
}
