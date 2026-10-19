package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * コードのコメントにある、仕様書と CLAUDE.md の節への参照が、実在する節を指しているかを確かめる。設計の意図は仕様書に書き、コメントは節への
 * 参照にする（CLAUDE.md の「コメントとドキュメントの置き場所」）ので、節の名前を変えて参照が切れたら、ここで気づく。
 *
 * <ul>
 *   <li>{@code phase2-spec.md の「6. 処理の詳細 > 日付の範囲」}：見出しを {@code >} でたどれる（後ろの見出しは、前の見出しの中にある）
 *   <li>{@code CLAUDE.md の「Spring・Lombok」}：CLAUDE.md の見出し
 *   <li>{@code CLAUDE.md の禁止事項「ログ」}：CLAUDE.md の禁止事項の太字の項目（{@code - **ログ**}）
 * </ul>
 *
 * <p>見出しは、参照の文字列で始まっていればよい（{@code 「3. ドメインモデル」} は {@code ## 3. ドメインモデル（`domain/`）} に当たる）。
 */
class DocReferenceTest {

    /** {@code xxx.md の「…」}。続けて書いた {@code 「…」「…」} も拾う。 */
    private static final Pattern REFERENCE =
            Pattern.compile("(phase1-spec|phase2-spec|phases|mymanifesto|spec|CLAUDE)\\.md の(禁止事項)?((?:「[^」]+」)+)");

    /** コメントの改行と、次の行の頭の「*」「//」（Javadoc の終わりの「*／」は除く）。 */
    private static final Pattern CONTINUATION = Pattern.compile("\\R\\s*(?:\\*(?!/)|//)\\s?");

    private static final Pattern QUOTED = Pattern.compile("「([^」]+)」");

    private static final Pattern HEADING = Pattern.compile("^(#+) (.+)$");

    private static final Pattern BOLD_ITEM = Pattern.compile("^- \\*\\*([^*]+)\\*\\*");

    private record Heading(int level, String text) {}

    private static Path docOf(String name) {
        return "CLAUDE".equals(name) ? Path.of("CLAUDE.md") : Path.of("docs", name + ".md");
    }

    private static List<Heading> headingsOf(Path doc) throws IOException {
        var headings = new ArrayList<Heading>();
        var inCodeBlock = false;
        for (var line : Files.readAllLines(doc, StandardCharsets.UTF_8)) {
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            var matcher = HEADING.matcher(line);
            if (!inCodeBlock && matcher.matches()) {
                headings.add(new Heading(matcher.group(1).length(), matcher.group(2)));
            }
        }
        return headings;
    }

    /** {@code path}（{@code >} で区切った見出し）を、見出しの入れ子のとおりにたどれるか。 */
    private static boolean resolves(List<Heading> headings, List<String> path) {
        var from = 0;
        var to = headings.size();
        var parentLevel = 0;
        for (var segment : path) {
            var found = -1;
            for (var i = from; i < to; i++) {
                var heading = headings.get(i);
                if (heading.level() > parentLevel && heading.text().startsWith(segment)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                return false;
            }
            parentLevel = headings.get(found).level();
            from = found + 1;
            to = headings.size();
            for (var i = from; i < headings.size(); i++) {
                if (headings.get(i).level() <= parentLevel) {
                    to = i;
                    break;
                }
            }
        }
        return true;
    }

    private static boolean hasBoldItem(Path doc, String item) throws IOException {
        for (var line : Files.readAllLines(doc, StandardCharsets.UTF_8)) {
            var matcher = BOLD_ITEM.matcher(line);
            if (matcher.find() && matcher.group(1).equals(item)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> sourceFiles() throws IOException {
        try (var paths = Files.walk(Path.of("src"))) {
            return paths.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .toList();
        }
    }

    @Test
    void コメントの仕様書とCLAUDE_mdへの参照は実在する節を指す() throws IOException {
        var checked = 0;
        var broken = new ArrayList<String>();
        for (var file : sourceFiles()) {
            // Javadoc や行コメントの途中で折り返した参照も拾えるよう、行の頭の「*」「//」を外してつなぐ
            var text = CONTINUATION
                    .matcher(Files.readString(file, StandardCharsets.UTF_8))
                    .replaceAll("");
            var matcher = REFERENCE.matcher(text);
            while (matcher.find()) {
                var doc = docOf(matcher.group(1));
                var quoted = QUOTED.matcher(matcher.group(3));
                while (quoted.find()) {
                    var target = quoted.group(1);
                    var ok = matcher.group(2) != null
                            ? hasBoldItem(doc, target)
                            : resolves(headingsOf(doc), List.of(target.split(" > ")));
                    checked++;
                    if (!ok) {
                        broken.add("%s %s の「%s」".formatted(file, doc, target));
                    }
                }
            }
        }

        // 参照を拾えている（このテストの Javadoc の例を除いても、本体のコメントに参照がある）
        assertThat(checked).isGreaterThan(10);
        assertThat(broken).isEmpty();
    }
}
