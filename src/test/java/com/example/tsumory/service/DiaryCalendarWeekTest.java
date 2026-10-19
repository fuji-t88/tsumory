package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiaryCalendarWeekTest {

    private static final DiaryCalendarDay DAY =
            new DiaryCalendarDay(LocalDate.of(2026, 10, 1), true, true, true, false);

    @Test
    void 渡したリストを後から変えても週の日は変わらない() {
        var days = new ArrayList<>(List.of(DAY));

        var week = new DiaryCalendarWeek(days);
        days.clear();

        assertThat(week.days()).containsExactly(DAY);
    }

    @Test
    void 週の日のリストは書き換えられない() {
        var week = new DiaryCalendarWeek(new ArrayList<>(List.of(DAY)));

        assertThatThrownBy(() -> week.days().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
