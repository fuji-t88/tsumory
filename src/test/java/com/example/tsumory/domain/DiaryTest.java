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
    void その日が来る前の生成時刻では作れない() {
        // 内部一貫性の制約：その日が来る前に、その日の日記は書けない。世界でいちばん早く 10/8 になる UTC+18:00 の 0 時
        // （UTC の 10/7 6:00）が境目
        var user = TestFixtures.hanako();
        var earliestStart = Instant.parse("2026-10-07T06:00:00Z");

        assertThatThrownBy(() ->
                        new Diary(user, TestFixtures.TODAY, TestFixtures.DIARY_CONTENT, earliestStart.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Diary cannot be generated before its date starts");
        assertThat(new Diary(user, TestFixtures.TODAY, TestFixtures.DIARY_CONTENT, earliestStart).getGeneratedAt())
                .isEqualTo(earliestStart);
        // 日本時間の 10/8 0 時ちょうど（UTC の 10/7 15:00）にも書ける
        assertThat(new Diary(
                                user,
                                TestFixtures.TODAY,
                                TestFixtures.DIARY_CONTENT,
                                TestFixtures.jst("2026-10-08T00:00:00"))
                        .getDiaryDate())
                .isEqualTo(TestFixtures.TODAY);
    }

    @Test
    void タイムゾーンを持たないので日本時間で9時間ぶんの矛盾は見逃す() {
        // 日本時間の前日 16 時は、日本時間ではまだ 10/8 が来ていないが、UTC+18:00 ではもう 10/8。エンティティでは弾かない
        // （未来の日付の日記を断るのは service の業務ルール）
        var generatedAt = TestFixtures.jst("2026-10-07T16:00:00");

        assertThat(new Diary(TestFixtures.hanako(), TestFixtures.TODAY, TestFixtures.DIARY_CONTENT, generatedAt)
                        .getGeneratedAt())
                .isEqualTo(generatedAt);
    }

    @Test
    void その日が来る前の生成時刻では書き直せず前の日記のまま() {
        var diary = newDiary();

        assertThatThrownBy(() -> diary.updateContent("書き直した日記", Instant.parse("2026-10-07T05:59:59Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(diary.getContent()).isEqualTo(TestFixtures.DIARY_CONTENT);
        assertThat(diary.getGeneratedAt()).isEqualTo(GENERATED_AT);
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
