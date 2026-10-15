package com.example.tsumory.controller;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.service.DiaryGenerationException;
import com.example.tsumory.service.DiaryGenerationService;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.NoPostsException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/diary")
@RequiredArgsConstructor
public class DiaryController {

    static final String NO_POSTS_MESSAGE = "つぶやきがないため日記を生成できません";
    static final String GENERATION_FAILED_MESSAGE = "日記の生成に失敗しました。時間をおいて再度お試しください";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final DiaryService diaryService;
    private final DiaryGenerationService diaryGenerationService;
    private final Clock clock;

    @GetMapping("/{date}")
    public String view(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails principal,
            Model model) {
        var diary = diaryService.findByDate(principal.userId(), date);
        model.addAttribute("date", date);
        // 今日の画面かどうかで、ヘッダーの選択中のメニューと「日記を書く」「作り直す」を出すかを決める
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("diary", diary.orElse(null));
        diary.ifPresent(d -> model.addAttribute(
                "generatedAtTime",
                d.getGeneratedAt().atZone(clock.getZone()).toLocalTime().format(TIME_FORMATTER)));
        return "diary";
    }

    @PostMapping("/generate")
    public String generate(@AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes flash) {
        var userId = principal.userId();
        var today = LocalDate.now(clock);
        try {
            var diary = diaryGenerationService.generateToday(userId);
            // 生成中に 0 時をまたぐと、押したときの「今日」と保存された日記の日付がずれるので、保存された日付へ移る
            return "redirect:/diary/" + diary.getDiaryDate();
        } catch (NoPostsException e) {
            flash.addFlashAttribute("message", NO_POSTS_MESSAGE);
            return "redirect:/";
        } catch (DiaryGenerationException e) {
            flash.addFlashAttribute("message", GENERATION_FAILED_MESSAGE);
            return "redirect:/diary/" + today;
        }
    }
}
