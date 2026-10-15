package com.example.tsumory.service;

import java.time.LocalDate;

/**
 * 日記一覧（カレンダー表示）の 1 マス。月の 1 日より前と末日より後の空きマスは {@link #blank()} で作る。
 *
 * @param date 日付。空きマスなら {@code null}
 * @param available 開ける日付（登録日から今日まで）なら {@code true}。リンクにするかを決める
 * @param today 今日なら {@code true}
 */
public record DiaryCalendarDay(LocalDate date, boolean hasDiary, boolean hasPosts, boolean available, boolean today) {

    static DiaryCalendarDay blank() {
        return new DiaryCalendarDay(null, false, false, false, false);
    }

    public boolean isBlank() {
        return date == null;
    }
}
