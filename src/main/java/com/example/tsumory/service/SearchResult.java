package com.example.tsumory.service;

import java.util.List;

/**
 * 検索結果。
 *
 * @param hits つぶやきと日記を混ぜて新しい順に並べたもの
 * @param postsTruncated つぶやきが上限（50 件）を超えて、一部だけを返したら {@code true}
 * @param diariesTruncated 日記が上限（50 件）を超えて、一部だけを返したら {@code true}
 */
public record SearchResult(List<SearchHit> hits, boolean postsTruncated, boolean diariesTruncated) {

    public SearchResult {
        hits = List.copyOf(hits);
    }

    public boolean truncated() {
        return postsTruncated || diariesTruncated;
    }
}
