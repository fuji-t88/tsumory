package com.example.tsumory.service;

import java.time.YearMonth;
import java.util.List;

/**
 * 日記一覧の 1 か月分。
 *
 * @param days 日記かつぶやきのある日（新しい日付から順）
 * @param weeks カレンダー表示の週（日曜始まり）
 * @param diaryCount その月の日記の本数
 * @param previousMonth 前の月。登録月より前で移れなければ {@code null}
 * @param nextMonth 次の月。今月より後で移れなければ {@code null}
 */
public record DiaryMonthSummary(
        YearMonth month,
        List<DiaryDaySummary> days,
        List<DiaryCalendarWeek> weeks,
        long diaryCount,
        YearMonth previousMonth,
        YearMonth nextMonth) {

    public DiaryMonthSummary {
        days = List.copyOf(days);
        weeks = List.copyOf(weeks);
    }
}
