package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikeKeywordTest {

    @Test
    void LIKEで特別な意味を持つ文字の前にエスケープ文字を付ける() {
        assertThat(LikeKeyword.escape("Curry")).isEqualTo("Curry");
        // ユーザーが % や _ を入力しても、全件や任意の 1 文字に一致させない
        assertThat(LikeKeyword.escape("100%")).isEqualTo("100!%");
        assertThat(LikeKeyword.escape("snake_case")).isEqualTo("snake!_case");
        // エスケープ文字の ! 自身も、先にエスケープする（二重にならない）
        assertThat(LikeKeyword.escape("達成率100%!")).isEqualTo("達成率100!%!!");
        // ESCAPE '!' にしたので、\ は普通の文字
        assertThat(LikeKeyword.escape("C:\tmp")).isEqualTo("C:\tmp");
    }
}
