package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tsumory.service.HighlightedText.Segment;
import org.junit.jupiter.api.Test;

class HighlightedTextTest {

    @Test
    void キーワードに一致した部分を英字の大文字と小文字を区別せずにすべて区切る() {
        assertThat(HighlightedText.of("Java and JAVA", "java").segments())
                .containsExactly(new Segment("Java", true), new Segment(" and ", false), new Segment("JAVA", true));
    }

    @Test
    void 先頭と末尾で一致した部分も区切る() {
        assertThat(HighlightedText.of("カレーとカレー", "カレー").segments())
                .containsExactly(new Segment("カレー", true), new Segment("と", false), new Segment("カレー", true));
    }

    @Test
    void 一致しないかキーワードが空なら全体を1つにする() {
        assertThat(HighlightedText.of("寝坊した", "カレー").segments()).containsExactly(new Segment("寝坊した", false));
        assertThat(HighlightedText.of("寝坊した", "").segments()).containsExactly(new Segment("寝坊した", false));
    }

    @Test
    void HTMLのタグは強調の印にせず本文の文字として残す() {
        // 強調のための HTML は作らない。本文の < > はテンプレートの th:text がエスケープする
        assertThat(HighlightedText.of("<b>カレー</b>", "カレー").segments())
                .containsExactly(new Segment("<b>", false), new Segment("カレー", true), new Segment("</b>", false));
    }
}
