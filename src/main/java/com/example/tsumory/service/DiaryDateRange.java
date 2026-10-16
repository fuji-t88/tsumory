package com.example.tsumory.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 日記を開ける日付の範囲（両端を含む）。業務ルール「登録日から今日まで」は {@link DiaryService#availableRange} の 1 か所で決め、
 * 日記の表示・生成・日記一覧のカレンダーは、この範囲に問い合わせるだけにする（docs/phase2-spec.md の「6. 処理の詳細 > 日付の範囲」）。
 *
 * @param first 開ける最初の日（登録日）
 * @param last 開ける最後の日（今日）
 */
public record DiaryDateRange(LocalDate first, LocalDate last) {

    public DiaryDateRange {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(last, "last");
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(first) && !date.isAfter(last);
    }

    /** 範囲より後（まだ来ていない日）なら {@code true}。範囲より前（登録日より前）は {@code false}。 */
    public boolean isFuture(LocalDate date) {
        return date.isAfter(last);
    }

    /** 日記一覧で開ける最初の月（登録月）。 */
    public YearMonth firstMonth() {
        return YearMonth.from(first);
    }

    /** 日記一覧で開ける最後の月（今月）。 */
    public YearMonth lastMonth() {
        return YearMonth.from(last);
    }

    public boolean containsMonth(YearMonth month) {
        return !month.isBefore(firstMonth()) && !month.isAfter(lastMonth());
    }
}
