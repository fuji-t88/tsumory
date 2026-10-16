package com.example.tsumory.service;

import com.example.tsumory.domain.PostCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** 検索結果の 1 件。つぶやきか日記のどちらか。 */
public sealed interface SearchHit {

    /** 結果をつぶやき・日記を混ぜて新しい順に並べるための時刻。 */
    Instant sortKey();

    /** リンク先（{@code /diary/{date}}）にするその日の日付（日本時間）。 */
    LocalDate date();

    /** テンプレートで種類を見分けるため（Thymeleaf では型で分岐しにくい）。 */
    default boolean isDiary() {
        return this instanceof DiaryHit;
    }

    /** つぶやき。本文は全文を強調付きで持つ。 */
    record PostHit(LocalDate date, LocalTime time, HighlightedText content, PostCategory category, Instant sortKey)
            implements SearchHit {}

    /**
     * 日記。本文はキーワードの前後だけを切り出して強調付きで持つ。並べるときは、その日の 23:59:59 として比べる（{@code sortKey} は
     * {@link SearchService} が {@code Clock} のタイムゾーンで求める）。
     */
    record DiaryHit(LocalDate date, HighlightedText excerpt, Instant sortKey) implements SearchHit {}
}
