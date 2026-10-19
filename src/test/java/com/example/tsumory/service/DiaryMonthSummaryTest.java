package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiaryMonthSummaryTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final DiaryDaySummary DAY = new DiaryDaySummary(LocalDate.of(2026, 10, 1), true, "晴れ", 2);
    private static final DiaryCalendarWeek WEEK = new DiaryCalendarWeek(List.of(DiaryCalendarDay.blank()));

    @Test
    void 渡したリストを後から変えても日と週は変わらない() {
        var days = new ArrayList<>(List.of(DAY));
        var weeks = new ArrayList<>(List.of(WEEK));

        var summary = new DiaryMonthSummary(OCTOBER, days, weeks, 1, null, null);
        days.clear();
        weeks.clear();

        assertThat(summary.days()).containsExactly(DAY);
        assertThat(summary.weeks()).containsExactly(WEEK);
    }

    @Test
    void 日と週のリストは書き換えられない() {
        var summary = new DiaryMonthSummary(
                OCTOBER, new ArrayList<>(List.of(DAY)), new ArrayList<>(List.of(WEEK)), 1, null, null);

        assertThatThrownBy(() -> summary.days().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> summary.weeks().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
