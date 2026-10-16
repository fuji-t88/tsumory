package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * ソース（Java・SQL・テンプレート・設定など）に、見えない文字を直接書いていないかを確かめる。見えない文字は、エディタやレビュー（差分の表示）で
 * 見えず、どの文字を書いたかを読み取れない。テストの値やコメントで使うときは、エスケープ（\\uXXXX）で書く。
 *
 * <p>決まりを規約として書くだけでは、書き手が気づかないうちに破れる（文字が見えないので、書いた本人も気づけない）。そこでテストで守る。
 * 対象の文字は {@link Post#INVISIBLE_CHAR_CLASS} に、絵文字をつなぐゼロ幅接合子（U+200D）と異体字セレクタ（U+FE00〜U+FE0F）を
 * 足したもの。どちらもつぶやきの本文では使ってよいが、ソースで見えないことに変わりはない。
 */
class SourceInvisibleCharsTest {

    private static final Pattern INVISIBLE_IN_SOURCE =
            Pattern.compile(Post.INVISIBLE_CHAR_CLASS + "|[\\u200D\\uFE00-\\uFE0F]");

    /** 確かめるディレクトリ（プロジェクト直下から）。テストはプロジェクト直下で動く。 */
    private static final List<Path> ROOTS = List.of(Path.of("src"), Path.of("db"));

    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("java", "sql", "html", "js", "css", "yaml", "yml", "properties", "kts", "md", "txt");

    private static List<Path> sourceFiles() throws IOException {
        var files = new ArrayList<Path>();
        for (var root : ROOTS) {
            try (var paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(SourceInvisibleCharsTest::isText)
                        .forEach(files::add);
            }
        }
        return files;
    }

    private static boolean isText(Path file) {
        var name = file.getFileName().toString();
        var dot = name.lastIndexOf('.');
        return dot >= 0 && TEXT_EXTENSIONS.contains(name.substring(dot + 1));
    }

    /** 見えない文字の場所（ファイル・行・コードポイント）。文字そのものはメッセージに入れない（入れても見えない）。 */
    private static List<String> violationsIn(Path file) throws IOException {
        var violations = new ArrayList<String>();
        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (var i = 0; i < lines.size(); i++) {
            var matcher = INVISIBLE_IN_SOURCE.matcher(lines.get(i));
            while (matcher.find()) {
                violations.add(
                        "%s:%d U+%04X".formatted(file, i + 1, matcher.group().codePointAt(0)));
            }
        }
        return violations;
    }

    @Test
    void ソースに見えない文字を直接書かない() throws IOException {
        var files = sourceFiles();
        var violations = new ArrayList<String>();
        for (var file : files) {
            violations.addAll(violationsIn(file));
        }

        // 探す場所が空振りしていない（このテスト自身と、マイグレーションを読んでいる）
        assertThat(files)
                .contains(
                        Path.of("src/test/java/com/example/tsumory/domain/SourceInvisibleCharsTest.java"),
                        Path.of("src/main/resources/db/migration/V3__add_content_checks_to_posts.sql"));
        assertThat(violations).isEmpty();
    }
}
