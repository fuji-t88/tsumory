package com.example.tsumory.controller;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.form.DiaryGenerateForm;
import com.example.tsumory.service.DiaryGenerationResult;
import com.example.tsumory.service.DiaryGenerationService;
import com.example.tsumory.service.DiaryItem;
import com.example.tsumory.service.DiaryNotFoundException;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.PostService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class DiaryController {

    static final String GENERATED_MESSAGE = "日記を書きました";
    static final String NO_POSTS_MESSAGE = "つぶやくと日記を書けます";
    static final String IN_PROGRESS_MESSAGE = "いま日記を書いています。少し待ってから、この画面を開き直してください";
    static final String REFUSED_MESSAGE = "日記を書けませんでした。つぶやきを見直して、もう一度お試しください";
    static final String FAILED_MESSAGE = "日記を書けませんでした。時間をおいて、もう一度お試しください";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final DiaryService diaryService;
    private final DiaryGenerationService diaryGenerationService;
    private final PostService postService;

    /** 日記表示。今日と昨日以外の日付、形式が不正な日付は 404。 */
    @GetMapping("/diary/{date}")
    public String show(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        var diary = diaryService.findDiary(principal.userId(), date);
        model.addAttribute("date", date);
        model.addAttribute("isToday", date.equals(postService.today()));
        model.addAttribute("postCount", postService.countPosts(principal.userId(), date));
        model.addAttribute("diary", diary.map(DiaryController::toView).orElse(null));
        return "diary/show";
    }

    /** 日記の生成・再生成。結果ごとのメッセージを付けて、その日の日記表示へ戻る。 */
    @PostMapping("/diary/generate")
    public String generate(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute DiaryGenerateForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // 隠しフィールドなので、ふつうの操作では起きない（URL の日付が不正なときと同じく 404）
            throw new DiaryNotFoundException(form.date());
        }
        var flash = switch (diaryGenerationService.generate(principal.userId(), form.date())) {
            case DiaryGenerationResult.Generated generated -> new Flash("successMessage", GENERATED_MESSAGE);
            case DiaryGenerationResult.NoPosts noPosts -> new Flash("infoMessage", NO_POSTS_MESSAGE);
            case DiaryGenerationResult.InProgress inProgress -> new Flash("infoMessage", IN_PROGRESS_MESSAGE);
            case DiaryGenerationResult.Refused refused -> new Flash("errorMessage", REFUSED_MESSAGE);
            case DiaryGenerationResult.Failed failed -> new Flash("errorMessage", FAILED_MESSAGE);
        };
        redirectAttributes.addFlashAttribute(flash.name(), flash.message());
        // 日付は LocalDate に変換済みの値なので、リクエストの文字列をそのまま使うことにはならない
        return "redirect:/diary/" + form.date();
    }

    private static DiaryView toView(DiaryItem diary) {
        return new DiaryView(diary.content(), diary.generatedTime().format(TIME_FORMAT));
    }

    /** フラッシュメッセージ。{@code name} は画面での色（success: 緑、info: 青、error: 赤）。 */
    private record Flash(String name, String message) {}
}
