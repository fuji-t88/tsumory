package com.example.tsumory.controller;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.service.DiaryListService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class DiaryListController {

    private final DiaryListService diaryListService;
    private final Clock clock;

    /**
     * 月ごとの日記一覧。{@code month} がなければ今月。{@code yyyy-MM} でない値は {@link NotFoundExceptionHandler} が、範囲外の月は
     * {@code DiaryMonthNotFoundException} が 404 にする。{@code view} は {@code calendar} 以外ならリスト表示。
     */
    @GetMapping("/diaries")
    public String list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) String view,
            Model model) {
        var summary =
                diaryListService.summarize(principal.userId(), month == null ? diaryListService.currentMonth() : month);
        model.addAttribute("month", summary.month());
        model.addAttribute("view", "calendar".equals(view) ? "calendar" : "list");
        model.addAttribute("days", summary.days());
        model.addAttribute("weeks", summary.weeks());
        model.addAttribute("diaryCount", summary.diaryCount());
        model.addAttribute("previousMonth", summary.previousMonth());
        model.addAttribute("nextMonth", summary.nextMonth());
        // 日付の表示で、今年なら年を省くため
        model.addAttribute("today", LocalDate.now(clock));
        return "diaries";
    }
}
