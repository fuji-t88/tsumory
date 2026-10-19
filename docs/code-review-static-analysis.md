# 静的解析の結果

静的解析ツールをコード全体に当てた結果。指摘にしたものと、指摘にしなかったもの（その理由）を残す。コードを読んで行うレビューは [code-review-service.md](code-review-service.md)・[code-review-non-service.md](code-review-non-service.md)。

- 対象（2026-10-19 時点、コミット `5e6d65e`）：`src/main`・`src/test` の Java すべて
- 方法：`build.gradle.kts` は変えず、Gradle の init スクリプトで一時的にツールを当てた。ローカルの `spotlessApply` がソースを書き換えないよう、環境変数 `CI` を付けて動かした
  - `javac -Xlint:all`（`processing`・`serial` を除く。`-Xmaxwarns 10000`）
  - Error Prone 2.42.0（すべての検査を警告として出す。`UnicodeInCode` は 2 回目から外した。下の「指摘にしなかったもの」）
  - SpotBugs（Gradle プラグイン 6.4.8。effort は max、確度は low まで出す）。`src/main` だけ
  - PMD 7.17.0（`rulesets/java/quickstart.xml`）
  - `spotlessCheck`
- 指摘の重要度と状態の付け方は [code-review-non-service.md](code-review-non-service.md) の「指摘の状態の管理」と同じ

## 数字

| ツール | `5e6d65e`（解析した時点） | `f67b199`（指摘 1 の対応後） | `bfbb454`（指摘 5 の対応後） |
|---|---|---|---|
| `javac -Xlint:all` | 0 | 測っていない | 測っていない |
| Error Prone（`UnicodeInCode` を除く） | 6 | 測っていない | 測っていない |
| Error Prone の `UnicodeInCode` | 97（javac の警告の上限 100 件で切れた数。実際はもっと多い） | 測っていない | 測っていない |
| SpotBugs | 15 | 11（`DiaryMonthSummary` の 4 件が消えた） | 5（`DiaryCalendarWeek`・`HighlightedText`・`SearchResult` の 6 件が消えた。残りはすべて「指摘にしなかったもの」） |
| PMD（main） | 22 | 測っていない | 測っていない |
| PMD（test） | 303（うち `MethodNamingConventions` が 302） | 測っていない | 測っていない |
| `spotlessCheck` | 違反なし | 違反なし（`./gradlew build` で確認） | 違反なし（`./gradlew build` で確認） |
| テストの件数 | 363（すべて成功） | 365（すべて成功。指摘 1 のテストを 2 件足した） | 371（すべて成功。指摘 5 のテストを 6 件足した） |
| 呼び出し側で `List.copyOf` してから record に渡している場所 | 2（`DiaryListService#calendarWeeks`・`HighlightedText#of`） | 2 | 0（record のコンストラクタで写すので外した） |

## まとめ

| # | 重要度 | 観点 | 指摘 | 場所 | 状態 |
|---|---|---|---|---|---|
| 1 | 中 | 可読性・保守性 | 日記一覧の週のリストを、書き換えられる `ArrayList` のまま controller に渡している | `DiaryListService#calendarWeeks`・`DiaryMonthSummary` | 対応済み |
| 2 | 低 | 可読性・保守性 | `DiaryWriter#write` の JavaDoc に要約の 1 文がない | `DiaryWriter` | 未対応 |
| 3 | 低 | 可読性・保守性 | メソッド参照で書けるラムダがある | `ClaudeService`・`PostCategoryClassifier` | 未対応 |
| 4 | 低 | 可読性・保守性 | `name.equals("CLAUDE")` を、文字列の定数を先にした形で書いていない | `DocReferenceTest` | 未対応 |
| 5 | 低 | 可読性・保守性 | リストを持つほかの record（`DiaryCalendarWeek`・`HighlightedText`・`SearchResult`）は、書き換えられないリストを渡すことを呼び出し側に任せている | `DiaryCalendarWeek`・`HighlightedText`・`SearchResult` | 対応済み |

## 可読性・保守性

### 1. 日記一覧の週のリストが書き換えられる（中）

- 状態：対応済み
  - ✓ 対応済み（2026-10-19、コミット `f67b199`）：`DiaryMonthSummary` にコンパクトコンストラクタを足し、`days` と `weeks` を `List.copyOf` で写すようにした。どの呼び出し側から作っても、書き換えられないリストになる。テストは `DiaryMonthSummaryTest` に 2 件（渡したリストを後から変えても日と週は変わらない、日と週のリストは書き換えられない）。本体を直す前に 2 件とも失敗することを確かめた

- 見つけたもの：SpotBugs の `EI_EXPOSE_REP`・`EI_EXPOSE_REP2`
- 場所：`DiaryListService#calendarWeeks` が `ArrayList` を返し、それがそのまま `DiaryMonthSummary#weeks` になる
- 何が起きるか：今は誰もリストを書き換えないので、画面は正しく出る。ほかのリスト（`days` は `Stream#toList`、1 週の `days` は `List.copyOf`）は書き換えられないのに、ここだけ揃っていない。controller やテンプレートの改修でリストを並べ替えたり足したりすると、service が作った結果が知らないうちに変わる
- 直し方：呼び出し側（`calendarWeeks` の `return`）で `List.copyOf` するのでなく、record のコンストラクタで写す。呼び出し側が気をつけなくても守れる（CLAUDE.md の「リファクタリングとレビューを深める 4 つの姿勢」の「構造で守る」）

### 2. `DiaryWriter#write` の JavaDoc に要約がない（低）

- 状態：未対応
- 見つけたもの：Error Prone の `MissingSummary`
- 場所：`DiaryWriter.java:11`
- 何が起きるか：`@param`・`@return`・`@throws` だけで、メソッドが何をするかの 1 文がない。JavaDoc の一覧で説明が空になる
- 直し方の案：先頭に「その日のつぶやきから、日記の本文を書く。」のような 1 文を足す

### 3. メソッド参照で書けるラムダ（低）

- 状態：未対応
- 見つけたもの：PMD の `LambdaCanBeMethodReference`
- 場所：`ClaudeService.java:105`（`details -> details.category()`）、`ClaudeService.java:122`（`text -> text.text()`）、`PostCategoryClassifier.java:107`
- 直し方の案：`StopDetails::category`・`TextBlock::text` のようにメソッド参照にする。振る舞いは変わらないので、リファクタリングのコミットにする

### 4. 文字列の定数を先にした比較（低）

- 状態：未対応
- 見つけたもの：PMD の `LiteralsFirstInComparisons`
- 場所：`DocReferenceTest.java:44`
- 何が起きるか：`name` は文書の参照から取り出した値で `null` にならないので、今は問題がない。直す効果は小さい
- 直し方の案：`"CLAUDE".equals(name)` にする

### 5. リストを持つほかの record も、コンストラクタで写していない（低）

- 状態：対応済み
  - ✓ 対応済み（2026-10-19、コミット `05c5898`）：`DiaryCalendarWeek`・`HighlightedText`・`SearchResult` にコンパクトコンストラクタを足し、リストを `List.copyOf` で写すようにした。リストを持つ表示用の record は、これで 4 つとも同じ守り方になった。テストは 3 クラスに 2 件ずつ、計 6 件（渡したリストを後から変えても変わらない、リストは書き換えられない）。`DiaryCalendarWeekTest`・`SearchResultTest` は新しく作り、`HighlightedText` は既存の `HighlightedTextTest` に足した。本体を直す前に 6 件とも失敗することを確かめた
  - 前後のコミット：テストを足す前に、`HighlightedTextTest` の英語のテスト名を日本語に直した（テストの整理、`0a0b9a6`。CLAUDE.md の「テスト」の「そのテストクラスを直すときに、日本語に直す」）。対応のあと、呼び出し側に残った `List.copyOf`（`DiaryListService#calendarWeeks`・`HighlightedText#of`）を外した（リファクタリング、`bfbb454`。テストは 371 件のまま）
- 見つけたもの：SpotBugs の `EI_EXPOSE_REP`・`EI_EXPOSE_REP2`（指摘 1 の対応後に残った 11 件のうち 6 件）
- 場所：`DiaryCalendarWeek`・`HighlightedText`・`SearchResult`
- 何が起きるか：今の呼び出し側は、どれも書き換えられないリストを渡している（`DiaryCalendarWeek` は `List.copyOf`、`HighlightedText#of` は `List.copyOf`、`SearchService` は `Stream#toList`）。守りが呼び出し側にあるので、新しい呼び出し側が `ArrayList` を渡すと指摘 1 と同じことになる
- 直し方の案：指摘 1 と同じく、コンパクトコンストラクタで `List.copyOf` する。指摘 1 は「weeks の修正」として依頼された範囲だけを直したので、ここは別に扱った

## 指摘にしなかったもの（2026-10-19）

ツールは指摘したが、このプロジェクトでは直さないと判断したもの。前提が変わったら見直す（CLAUDE.md の「リファクタリングとレビューを深める 4 つの姿勢」の「更新する」）。

| ツールの指摘 | 件数 | 判断と理由 | 見直す条件 |
|---|---|---|---|
| Error Prone `UnicodeInCode`、PMD `MethodNamingConventions` | 399 | テスト名を日本語で書くのは CLAUDE.md の「テスト」の決まり | テスト名の決まりを変えたとき |
| Error Prone `UnusedVariable`（controller のテストの `userRepository`） | 4 | `@MockitoBean` で置いているのは、`@WebMvcTest` で読み込む `CustomUserDetailsService` が `UserRepository` を必要とするため。読まないのは意図どおりで、消すとテストの準備で失敗する | `CustomUserDetailsService` が `UserRepository` を使わなくなったとき |
| Error Prone `CanonicalDuration`（`ClaudePropertiesTest` の `Duration.ofSeconds(120)`） | 1 | 仕様（phase1-spec.md の「6. AI 連携」）が「120 秒」と書いているので、`ofMinutes(2)` より仕様と照らし合わせやすい | 仕様のタイムアウトの書き方を変えたとき |
| PMD `GuardLogStatement` | 18 | SLF4J のプレースホルダーで書いていて、渡す値（ID・件数・文字数・処理時間など）も軽い。レベルの事前判定を足すと、読みにくくなるだけ | ログの引数に重い処理（文字列の組み立てなど）を書くようになったとき |
| PMD `UseUtilityClass`（`TsumoryApplication`） | 1 | Spring Boot の起動クラスの標準の書き方 | なし |
| SpotBugs `CT_CONSTRUCTOR_THROW`（`Post`・`Diary`・`User`） | 3 | エンティティがコンストラクタで内部一貫性の制約を守るのは設計どおり（CLAUDE.md の「入力の検証（多層防御）」）。指摘の Finalizer 攻撃は、信頼できないコードが同じ JVM でサブクラスを作れることが前提で、このアプリには当てはまらない | 信頼できないコード（プラグインなど）を読み込むようになったとき |
| SpotBugs `VA_FORMAT_STRING_USES_NEWLINE`（`ClaudeService#buildUserMessage`） | 1 | `%n` は OS の改行になり、Windows では `\r\n` になる。プロンプトは OS によらず同じにしたいので、`\n` が正しい | なし |
| SpotBugs `THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION`（`SecurityConfig#filterChain`） | 1 | Spring Security の `HttpSecurity#build` が `throws Exception` と宣言している | Spring Security の宣言が変わったとき |

## よくできている点

- **コンパイラの警告が 0 件**：`-Xlint:all` で、raw 型・未検査の変換・非推奨の API などが 1 件もない。型で守られているので、改修で型の誤りが入るとコンパイルで気づける
- **Error Prone でバグのパターンが 0 件**：`UnicodeInCode` と書き方の指摘を除くと、本体（`src/main`）に出たのは JavaDoc の 1 件だけ。`Optional` の誤用・`equals` の取り違え・戻り値の捨て忘れなどがない
- **record のリストは、ほとんどの呼び出し側が書き換えられない形で渡している**：`Stream#toList` と `List.copyOf` を使い分けていて、指摘 1 の 1 か所だけが漏れていた。service が作った表示用の値を、controller・テンプレートで変えられないようになっている
