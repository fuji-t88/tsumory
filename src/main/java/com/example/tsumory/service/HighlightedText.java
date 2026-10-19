package com.example.tsumory.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 検索キーワードに一致した部分を強調するため、本文を「一致した部分」と「それ以外」に分けたもの。
 *
 * <p>テンプレートでは {@code th:each} と {@code th:text} で出し、{@code match} が {@code true} の部分だけ {@code <mark>} で囲む。
 * 強調のための HTML をサーバーで文字列として作らない（{@code th:utext} を使わないため。CLAUDE.md の禁止事項「XSS」）。
 */
public record HighlightedText(List<Segment> segments) {

    /** 本文の一部。{@code match} はキーワードに一致した部分なら {@code true}。 */
    public record Segment(String text, boolean match) {}

    public HighlightedText {
        segments = List.copyOf(segments);
    }

    /** {@code text} の中の {@code keyword} に一致する部分をすべて区切る。英字の大文字・小文字は区別しない。キーワードが空なら全体を 1 つにする。 */
    static HighlightedText of(String text, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return new HighlightedText(List.of(new Segment(text, false)));
        }
        var segments = new ArrayList<Segment>();
        var plainStart = 0;
        var i = 0;
        while (i <= text.length() - keyword.length()) {
            if (text.regionMatches(true, i, keyword, 0, keyword.length())) {
                if (plainStart < i) {
                    segments.add(new Segment(text.substring(plainStart, i), false));
                }
                segments.add(new Segment(text.substring(i, i + keyword.length()), true));
                i += keyword.length();
                plainStart = i;
            } else {
                i++;
            }
        }
        if (plainStart < text.length()) {
            segments.add(new Segment(text.substring(plainStart), false));
        }
        return new HighlightedText(segments);
    }

    /** {@code text} の中で最初に {@code keyword} に一致する位置。英字の大文字・小文字は区別しない。なければ -1。 */
    static int indexOfIgnoreCase(String text, String keyword) {
        for (var i = 0; i <= text.length() - keyword.length(); i++) {
            if (text.regionMatches(true, i, keyword, 0, keyword.length())) {
                return i;
            }
        }
        return -1;
    }
}
