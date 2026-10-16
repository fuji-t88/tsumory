package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DiaryTest {

    private static final Instant GENERATED_AT = TestFixtures.jst("2026-10-08T22:00:00");
    private static final Instant REGENERATED_AT = TestFixtures.jst("2026-10-08T23:00:00");

    /**
     * 日記の日付（{@link TestFixtures#TODAY}、10/8）の生成時刻の境目。世界でいちばん早く 10/8 になるオフセット（UTC+18:00）の 0 時で、
     * これより前の生成時刻は拒否する。
     */
    private static final Instant EARLIEST_START =
            OffsetDateTime.parse("2026-10-08T00:00:00+18:00").toInstant();

    // 例外のメッセージは、決まった文言と同じかで確かめる。日記の本文も個人的な内容なので、メッセージに入っていないことも確かめられる
    private static final String BLANK = "Diary content must not be blank";
    private static final String BEFORE_DATE = "Diary cannot be generated before its date starts";

    private final User user = TestFixtures.hanako();

    private Diary newDiary() {
        return new Diary(user, TestFixtures.TODAY, TestFixtures.DIARY_CONTENT, GENERATED_AT);
    }

    /** 書いたときのまま（本文・生成時刻・日付のどれも変わっていない）。失敗した書き直しが、一部だけ書き換えて止まっていないことを確かめる。 */
    private static void assertUnchanged(Diary diary) {
        assertSoftly(softly -> {
            softly.assertThat(diary.getContent()).isEqualTo(TestFixtures.DIARY_CONTENT);
            softly.assertThat(diary.getGeneratedAt()).isEqualTo(GENERATED_AT);
            softly.assertThat(diary.getDiaryDate()).isEqualTo(TestFixtures.TODAY);
        });
    }

    @Test
    void 必須の値がnullなら作れない() {
        // 保存の時点の DB のエラー（500）ではなく、作る時点で気づける
        var today = TestFixtures.TODAY;
        var content = TestFixtures.DIARY_CONTENT;

        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> new Diary(null, today, content, GENERATED_AT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("user");
            softly.assertThatThrownBy(() -> new Diary(user, null, content, GENERATED_AT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("diaryDate");
            softly.assertThatThrownBy(() -> new Diary(user, today, null, GENERATED_AT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("content");
            softly.assertThatThrownBy(() -> new Diary(user, today, content, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("now");
        });
    }

    @Test
    void 本文がnullなら書き直せず前の日記のまま() {
        var diary = newDiary();

        assertThatThrownBy(() -> diary.updateContent(null, REGENERATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("content");
        assertUnchanged(diary);
    }

    @Test
    void 時刻がnullなら書き直せず前の日記のまま() {
        var diary = newDiary();

        assertThatThrownBy(() -> diary.updateContent("書き直した日記", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
        // 本文だけが書き換わった状態で止まらない
        assertUnchanged(diary);
    }

    /** 空・空白だけの本文。作るときと書き直すときの両方のテストが読むので、ケースを足せば両方の経路で確かめられる。 */
    static Stream<Arguments> blankContents() {
        return Stream.of(argumentSet("空文字列", ""), argumentSet("空白と改行だけ", "  \n\n "), argumentSet("全角スペースだけ", "　"));
    }

    @ParameterizedTest
    @MethodSource("blankContents")
    void 空や空白だけの本文では作れない(String content) {
        // 「日記あり」なのに何も書かれていない画面にしない
        assertThatThrownBy(() -> new Diary(user, TestFixtures.TODAY, content, GENERATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(BLANK);
    }

    @ParameterizedTest
    @MethodSource("blankContents")
    void 空や空白だけの本文では書き直せず前の日記のまま(String content) {
        // 前の日記を空で上書きして消さない
        var diary = newDiary();

        assertThatThrownBy(() -> diary.updateContent(content, REGENERATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(BLANK);
        assertUnchanged(diary);
    }

    @Test
    void 段落の改行を含む本文は使える() {
        // 日記は AI の出力。つぶやきと違い、改行は禁止しない
        var content = "朝は寝坊した。\n\n昼はカレーを食べた。";

        assertThat(new Diary(user, TestFixtures.TODAY, content, GENERATED_AT).getContent())
                .isEqualTo(content);
    }

    @Test
    void 境目ちょうどの生成時刻なら作れる() {
        assertThat(new Diary(user, TestFixtures.TODAY, TestFixtures.DIARY_CONTENT, EARLIEST_START).getGeneratedAt())
                .isEqualTo(EARLIEST_START);
    }

    @Test
    void 境目より前の生成時刻では作れない() {
        // 内部一貫性の制約：その日が来る前に、その日の日記は書けない
        assertThatThrownBy(() ->
                        new Diary(user, TestFixtures.TODAY, TestFixtures.DIARY_CONTENT, EARLIEST_START.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(BEFORE_DATE);
    }

    @Test
    void 日本時間の0時ちょうどに作れる() {
        assertThatCode(() -> new Diary(
                        user, TestFixtures.TODAY, TestFixtures.DIARY_CONTENT, TestFixtures.jst("2026-10-08T00:00:00")))
                .doesNotThrowAnyException();
    }

    @Test
    void タイムゾーンを持たないので日本時間で9時間ぶんの矛盾は見逃す() {
        // 日本時間の前日 16 時は、日本時間ではまだ 10/8 が来ていないが、UTC+18:00 ではもう 10/8。エンティティでは弾かない
        // （未来の日付の日記を断るのは service の業務ルール）
        var generatedAt = TestFixtures.jst("2026-10-07T16:00:00");

        assertThat(new Diary(user, TestFixtures.TODAY, TestFixtures.DIARY_CONTENT, generatedAt).getGeneratedAt())
                .isEqualTo(generatedAt);
    }

    @Test
    void 境目より前の生成時刻では書き直せず前の日記のまま() {
        var diary = newDiary();

        assertThatThrownBy(() -> diary.updateContent("書き直した日記", EARLIEST_START.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(BEFORE_DATE);
        assertUnchanged(diary);
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
