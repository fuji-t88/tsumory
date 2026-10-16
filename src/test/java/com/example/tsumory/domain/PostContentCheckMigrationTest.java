package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * DB の検査制約（V3）に書き出した文字の範囲が、Java の定義（{@link Post#INVISIBLE_CHAR_CLASS}・{@link Character#isWhitespace}）と
 * 同じかを確かめる。PostgreSQL の正規表現は Unicode の文字の種類を使えないので、範囲を書き出してある。片方だけ変えたり、Java の
 * Unicode の版が上がって当てはまる文字が増えたりしたら、ここで気づく。
 */
class PostContentCheckMigrationTest {

    private static final String MIGRATION = "db/migration/V3__add_content_checks_to_posts.sql";

    private static String migration() throws IOException {
        return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
    }

    /** 制約の CHECK の中の、角かっこで囲んだ文字の範囲（先頭の ^ は除く）。 */
    private static String rangesOf(String sql, String constraintName) {
        var matcher = Pattern.compile(constraintName + "\\s+CHECK \\(content !?~ '\\[\\^?([^\\]]*)\\]'\\)")
                .matcher(sql);
        assertThat(matcher.find()).as(constraintName).isTrue();
        return matcher.group(1);
    }

    /** {@code predicate} に当てはまる文字（U+0001 から）を、PostgreSQL の正規表現の範囲（\\uXXXX・\\UXXXXXXXX）で書き出す。 */
    private static String rangesFor(IntPredicate predicate) {
        var ranges = new StringBuilder();
        var cp = 1;
        while (cp <= Character.MAX_CODE_POINT) {
            if (predicate.test(cp)) {
                var start = cp;
                while (cp < Character.MAX_CODE_POINT && predicate.test(cp + 1)) {
                    cp++;
                }
                ranges.append(escape(start));
                if (cp > start) {
                    ranges.append('-').append(escape(cp));
                }
            }
            cp++;
        }
        return ranges.toString();
    }

    private static String escape(int cp) {
        return cp <= 0xFFFF ? "\\u%04X".formatted(cp) : "\\U%08X".formatted(cp);
    }

    @Test
    void 見えない文字の範囲はPostの定義と同じ() throws IOException {
        var invisible = Pattern.compile(Post.INVISIBLE_CHAR_CLASS);

        assertThat(rangesOf(migration(), "posts_content_no_invisible_chars"))
                .isEqualTo(rangesFor(
                        cp -> invisible.matcher(Character.toString(cp)).matches()));
    }

    @Test
    void 空白の文字の範囲はJavaのisBlankと同じ() throws IOException {
        assertThat(rangesOf(migration(), "posts_content_not_blank")).isEqualTo(rangesFor(Character::isWhitespace));
    }

    @Test
    void マイグレーション自体に見えない文字を直接書かない() throws IOException {
        // 範囲は \\uXXXX の形で書く。文字そのものを書くと、エディタやレビューで見えず、誤りに気づけない
        var invisible = Pattern.compile(Post.INVISIBLE_CHAR_CLASS);

        assertThat(invisible.matcher(migration()).find()).isFalse();
    }

    @Test
    void 改行はDBの制約では止めない() {
        // フェーズ 1 の改行入りのつぶやきを UPDATE（カテゴリの変更）できるようにするため。改行はフォーム・Post・プロンプトで守る
        var invisible = Pattern.compile(Post.INVISIBLE_CHAR_CLASS);

        assertThat(invisible.matcher("\r").find()).isFalse();
        assertThat(invisible.matcher("\n").find()).isFalse();
    }
}
