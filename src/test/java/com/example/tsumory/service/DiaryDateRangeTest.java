package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class DiaryDateRangeTest {

    /** 登録日 2026-09-10 から今日 2026-10-15 まで。 */
    private final DiaryDateRange range = new DiaryDateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 15));

    @Test
    void contains_includesBothEnds() {
        assertThat(range.contains(LocalDate.of(2026, 9, 10))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 10, 15))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 9, 9))).isFalse();
        assertThat(range.contains(LocalDate.of(2026, 10, 16))).isFalse();
    }

    @Test
    void isFuture_onlyAfterTheLastDay() {
        // 日記の生成は、未来（メッセージを付けてホームへ）と登録日より前（404）で応答を変えるので、2 つを見分けられる
        assertThat(range.isFuture(LocalDate.of(2026, 10, 16))).isTrue();
        assertThat(range.isFuture(LocalDate.of(2026, 10, 15))).isFalse();
        assertThat(range.isFuture(LocalDate.of(2026, 9, 9))).isFalse();
    }

    @Test
    void months_fromRegistrationMonthToCurrentMonth() {
        assertThat(range.firstMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(range.lastMonth()).isEqualTo(YearMonth.of(2026, 10));
        // 月は日にちを問わない（登録日が 9/10 でも 9 月は開ける）
        assertThat(range.containsMonth(YearMonth.of(2026, 9))).isTrue();
        assertThat(range.containsMonth(YearMonth.of(2026, 10))).isTrue();
        assertThat(range.containsMonth(YearMonth.of(2026, 8))).isFalse();
        assertThat(range.containsMonth(YearMonth.of(2026, 11))).isFalse();
    }

    @Test
    void requiresBothEnds() {
        assertThatThrownBy(() -> new DiaryDateRange(null, LocalDate.of(2026, 10, 15)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DiaryDateRange(LocalDate.of(2026, 9, 10), null))
                .isInstanceOf(NullPointerException.class);
    }
}
