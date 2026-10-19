package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tsumory.service.HighlightedText.Segment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchResultTest {

    private static final SearchHit HIT = new SearchHit.DiaryHit(
            LocalDate.of(2026, 10, 1),
            new HighlightedText(List.of(new Segment("カレーの日", false))),
            Instant.parse("2026-10-01T14:59:59Z"));

    @Test
    void 渡したリストを後から変えても検索結果は変わらない() {
        var hits = new ArrayList<>(List.of(HIT));

        var result = new SearchResult(hits, false, false);
        hits.clear();

        assertThat(result.hits()).containsExactly(HIT);
    }

    @Test
    void 検索結果のリストは書き換えられない() {
        var result = new SearchResult(new ArrayList<>(List.of(HIT)), false, false);

        assertThatThrownBy(() -> result.hits().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
