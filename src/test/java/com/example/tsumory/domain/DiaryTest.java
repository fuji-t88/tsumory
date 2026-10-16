package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DiaryTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-10-08T13:00:00Z");
    private static final Instant REGENERATED_AT = Instant.parse("2026-10-08T14:00:00Z");

    private static Diary newDiary() {
        return new Diary(TestFixtures.hanako(), TestFixtures.TODAY, TestFixtures.DIARY_CONTENT, GENERATED_AT);
    }

    @Test
    void 必須の値がnullなら作れない() {
        // 保存の時点の DB のエラー（500）ではなく、作る時点で気づける
        var user = TestFixtures.hanako();
        var today = TestFixtures.TODAY;
        var content = TestFixtures.DIARY_CONTENT;

        assertThatThrownBy(() -> new Diary(null, today, content, GENERATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("user");
        assertThatThrownBy(() -> new Diary(user, null, content, GENERATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("diaryDate");
        assertThatThrownBy(() -> new Diary(user, today, null, GENERATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("content");
        assertThatThrownBy(() -> new Diary(user, today, content, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
    }

    @Test
    void 時刻がnullなら書き直せず前の日記のまま() {
        var diary = newDiary();

        assertThatThrownBy(() -> diary.updateContent("書き直した日記", null)).isInstanceOf(NullPointerException.class);
        // 本文だけが書き換わった状態で止まらない
        assertThat(diary.getContent()).isEqualTo(TestFixtures.DIARY_CONTENT);
        assertThat(diary.getGeneratedAt()).isEqualTo(GENERATED_AT);
    }

    @Test
    void 空や空白だけの本文では作れない() {
        // 「日記あり」なのに何も書かれていない画面にしない
        var user = TestFixtures.hanako();
        for (var content : new String[] {"", "  \n\n ", "　"}) {
            assertThatThrownBy(() -> new Diary(user, TestFixtures.TODAY, content, GENERATED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 空の本文では書き直せず前の日記のまま() {
        // 前の日記を空で上書きして消さない
        var diary = newDiary();

        assertThatThrownBy(() -> diary.updateContent(" ", REGENERATED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThat(diary.getContent()).isEqualTo(TestFixtures.DIARY_CONTENT);
        assertThat(diary.getGeneratedAt()).isEqualTo(GENERATED_AT);
    }

    @Test
    void 段落の改行を含む本文は使える() {
        // 日記は AI の出力。つぶやきと違い、改行は禁止しない
        var content = "朝は寝坊した。\n\n昼はカレーを食べた。";

        assertThat(new Diary(TestFixtures.hanako(), TestFixtures.TODAY, content, GENERATED_AT).getContent())
                .isEqualTo(content);
    }

    @Test
    void 書き直すと本文と生成時刻が変わり日付は変わらない() {
        var diary = newDiary();

        diary.updateContent("書き直した日記", REGENERATED_AT);

        assertThat(diary.getContent()).isEqualTo("書き直した日記");
        assertThat(diary.getGeneratedAt()).isEqualTo(REGENERATED_AT);
        assertThat(diary.getDiaryDate()).isEqualTo(TestFixtures.TODAY);
    }
}
