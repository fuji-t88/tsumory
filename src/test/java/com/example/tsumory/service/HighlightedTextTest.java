package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tsumory.service.HighlightedText.Segment;
import org.junit.jupiter.api.Test;

class HighlightedTextTest {

    @Test
    void of_splitsEveryMatchIgnoringCase() {
        assertThat(HighlightedText.of("Java and JAVA", "java").segments())
                .containsExactly(new Segment("Java", true), new Segment(" and ", false), new Segment("JAVA", true));
    }

    @Test
    void of_matchAtStartAndEnd() {
        assertThat(HighlightedText.of("カレーとカレー", "カレー").segments())
                .containsExactly(new Segment("カレー", true), new Segment("と", false), new Segment("カレー", true));
    }

    @Test
    void of_noMatchOrEmptyKeyword_isOneSegment() {
        assertThat(HighlightedText.of("寝坊した", "カレー").segments()).containsExactly(new Segment("寝坊した", false));
        assertThat(HighlightedText.of("寝坊した", "").segments()).containsExactly(new Segment("寝坊した", false));
    }

    @Test
    void of_keepsHtmlAsPlainText() {
        // 強調のための HTML は作らない。本文の < > はテンプレートの th:text がエスケープする
        assertThat(HighlightedText.of("<b>カレー</b>", "カレー").segments())
                .containsExactly(new Segment("<b>", false), new Segment("カレー", true), new Segment("</b>", false));
    }
}
