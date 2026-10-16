# サービス層のコードレビュー

CLAUDE.md の「コードレビューの観点」（可読性・保守性、エラーハンドリング、セキュリティ）に沿って、サービス層をレビューした結果。

- 対象：`src/main/java/com/example/tsumory/service/` の全 24 ファイル（2026-10-15 時点、コミット `dd7ca38`）
  - サービス：`PostService`・`DiaryService`・`DiaryGenerationService`・`DiaryListService`・`SearchService`
  - AI 連携：`ClaudeService`（`DiaryWriter` の実装）・`PostCategoryClassifier`
  - 値・例外：`PostItem`・`DiaryItem`・`SearchHit`・`SearchResult`・`HighlightedText`・`DiaryDaySummary`・`DiaryCalendarDay`・`DiaryCalendarWeek`・`DiaryMonthSummary`、各例外クラス
- 方法：コードを読んで観点と突き合わせた。コードは変えていない。指摘の重要度は次のとおり
- 追加（2026-10-16）：指摘 11 は、「未来日付チェックが表示と生成の 2 か所に重複している気がする。業務ルールを一箇所にまとめたい」という問いを受けて、日付の範囲の判定を洗い出して足した（調べると 3 か所だった）
  - **高**：利用者に見える不具合や、セキュリティの穴になりうる
  - **中**：今は動くが、条件しだいで不具合になる。または、直さないと保守で誤りを招きやすい
  - **低**：読みやすさ・一貫性の問題。動作には影響しない

### 指摘の状態の管理

このファイルは、指摘ごとの対応を追う設計記録として使う。チャットのやりとりは流れて消えるので、判断はここに残す。

- 状態は次の 3 つ。まとめの表の「状態」の列と、各指摘の「状態」の行の両方を直す
  - **未対応**：まだ手を付けていない
  - **対応済み**：直した。各指摘の下に「✓ 対応済み」と解消メモ（日付、何をどう直したか、コミット）を書き足す
  - **保留**：直さないと決めた、または後回しにした。理由（誰がいつ、なぜそう判断したか）を書く
- 解消メモの書き方の例

  ```
  - 状態：対応済み
    - ✓ 対応済み（2026-10-16、コミット abc1234）：buildUserMessage で本文の改行を空白に置き換えた。改行入りの既存データでテストを足した
  ```

- 指摘を消さない。対応済み・保留になっても残し、後で「あの指摘はどう判断したか」を振り返る一次情報にする

## まとめ

| # | 重要度 | 観点 | 指摘 | 場所 | 状態 |
|---|---|---|---|---|---|
| 1 | 中 | セキュリティ | 改行を含む既存のつぶやきが、日記のプロンプトに偽の行を紛れ込ませられる | `ClaudeService#buildUserMessage` | 対応済み |
| 2 | 中 | エラーハンドリング | 同じ日の日記を同時に生成すると、2 件目が一意制約の違反で 500 になる | `DiaryService#saveDiary` | 対応済み |
| 3 | 中 | 可読性・保守性 | タイムゾーンの基準が `Clock` と定数 `PostService.ZONE` の 2 つに分かれている | `PostService`・`DiaryService`・`SearchService`・`SearchHit` | 対応済み |
| 4 | 中 | 可読性・保守性 | 同じ処理が 2 か所ずつある（`LIKE` のエスケープ、プロンプトのエスケープ、日記の書き出しの切り出し、Claude API の失敗ログ） | `SearchService`・`DiaryService`・`ClaudeService`・`PostCategoryClassifier`・`DiaryListService` | 対応済み |
| 5 | 低 | 可読性・保守性 | Javadoc が古い（存在しないメソッドへのリンク、今は違う日付の範囲） | `DiaryService#findDiaryForSummary`・`PostService#isEditableDate` | 対応済み |
| 6 | 低 | 可読性・保守性 | ログメッセージの言語が 1 か所だけ日本語 | `PostService#create` | 保留 |
| 7 | 低 | エラーハンドリング | キーワードもカテゴリもなしで `search` を呼ぶと、黙って 0 件を返す | `SearchService#search` | 対応済み |
| 8 | 低 | 可読性・保守性 | 「他人のつぶやきなら見つからない扱い」の取得処理が 2 か所に重複 | `PostService#updateCategory`・`#findEditablePost` | 対応済み |
| 9 | 低 | 可読性・保守性 | 本番のコードから呼ばれていないメソッドがある | `PostService#findTodayPosts` | 保留 |
| 10 | 低 | 可読性・保守性 | つぶやきはリポジトリ、日記はサービスを経由していて、取り方がそろっていない | `SearchService` | 対応済み |
| 11 | 中 | 可読性・保守性 | 業務ルール「日記を開ける日付は登録日から今日まで」の判定が 3 か所にある（日記の表示・生成・日記一覧のカレンダー） | `DiaryService`・`DiaryGenerationService`・`DiaryListService` | 未対応 |

高の指摘はない。セキュリティの基本（他人のデータの絞り込み、SQL のパラメータ化、ログに本文を出さない、画面に内部情報を出さない）は満たしている（末尾の「よくできている点」）。

## 可読性・保守性

### 3. タイムゾーンの基準が 2 つある（中）

- 状態：対応済み
  - ✓ 対応済み（2026-10-16、コミット `2c25f23`）：定数 `PostService.ZONE` を廃止し、日付・時刻の変換はすべて `clock.getZone()`・`LocalDate.now(clock)` で求めるようにした（`PostService`・`DiaryService`・`SearchService`）。`SearchHit.DiaryHit` は並べるための時刻を自分で求めず、`Clock` を持つ `SearchService` が求めて `sortKey` として渡す形にした。テストの `PostService.ZONE` は `TestFixtures.JST` に置き換え、`PostServiceTest` に「同じ瞬間でも、UTC の `Clock` なら日付が前日になる」テストを足した

- 場所
  - `Clock` から求めている：`PostService#findPostsByDate`・`#countPosts`・`#findTodayPosts`、`DiaryService#isAvailableDate`、`DiaryListService`、`DiaryGenerationService`
  - 定数 `PostService.ZONE`（`Asia/Tokyo`）を直接使っている：`PostService#today`（37 行目）・`#create`（84 行目）・`#findEditablePost`（176 行目）、`DiaryService#registeredDate`（30 行目）・`#findDiaryForSummary`（59 行目）、`SearchService#toHit`（97 行目）、`SearchHit.DiaryHit#sortKey`
- 何が起きるか：今は `ClockConfig` の `Clock` も `Asia/Tokyo` なので結果は同じ。しかし、`Clock` のタイムゾーンを変える（本番用に差し替える、テストで別のゾーンを渡す）と、「今日」を `Clock` で求める処理と `ZONE` で求める処理で日付がずれる。たとえば `PostService#create` は `ZONE` で今日を求めて上限を数え、`#findTodayPosts` は `Clock` のゾーンで今日を求める
- 観点：CLAUDE.md の「時刻の取得」は「タイムゾーンの基準も `Clock` の 1 か所にまとまる」ことを理由に挙げている
- 直し方の案：「今日」や日付の変換は `LocalDate.now(clock)`・`clock.getZone()` に統一し、`PostService.ZONE` は `Clock` を持たない場所（エンティティから値を作る `PostItem.from` など）に渡す値としてだけ使う。または `ZONE` を廃止し、呼び出し側から `clock.getZone()` を渡す

### 4. 同じ処理が 2 か所ずつある（中）

- 状態：対応済み
  - 判断メモ（2026-10-15）：`DiaryService#escapeForLike` を `private` にしたのは書籍の原稿に合わせたため。`LIKE` のエスケープを共通化するなら、原稿との違いとして扱うかを決めてから直す
  - ✓ 対応済み（2026-10-16、コミット `a71de91`・`094adec`・`2026022`）：`LIKE` のエスケープは package-private の `LikeKeyword#escape` にまとめ、つぶやき（`PostService#searchPosts`）と日記（`DiaryService#searchDiaries`）の両方から呼ぶ。書籍の原稿に合わせて `private` にしていた `DiaryService#escapeForLike` はなくした（原稿との違いとして扱う）。プロンプトのエスケープと Claude API の失敗のログに出す値（経過時間・ステータス・`request-id`）は `ClaudeApiSupport` にまとめた。失敗のログは、応答がないときもステータスと `request-id` を `none` で出す 1 つの形になった。本文の切り出しは `SearchService#excerpt` もコードポイントで数えるようにした。テストは `LikeKeywordTest`・`ClaudeApiSupportTest` を足し、`SearchServiceTest` に絵文字を含む本文の切り出しを足した

| 処理 | 1 か所目 | 2 か所目 | 違い |
|---|---|---|---|
| `LIKE` のエスケープ（`!`・`%`・`_`） | `SearchService#escapeLike`（つぶやき） | `DiaryService#escapeForLike`（日記） | なし（同じ 1 行） |
| プロンプトのエスケープ（`&`・`<`・`>`） | `ClaudeService#escape` | `PostCategoryClassifier#escape` | なし |
| Claude API の失敗のログ（経過時間、ステータス、`request-id` の取り出し） | `ClaudeService#write` | `PostCategoryClassifier#classify` | メッセージの文言だけ |
| 本文の先頭の切り出し（改行を空白に、超えたら「…」） | `DiaryListService#excerpt`（60 文字。コードポイントで数える） | `SearchService#excerpt`（キーワードの前後。UTF-16 の位置で数え、サロゲートペアだけ補正） | 文字の数え方が違う |

- 何が起きるか：片方だけを直すと、もう片方と挙動がずれる。とくに `LIKE` のエスケープは、つぶやきと日記で同じ決まり（`ESCAPE '!'`）に頼っているので、ずれると片方だけ `%` が全件に一致するようになる（セキュリティにも関わる）
- 補足：`DiaryService#escapeForLike` を `private` にしているのは書籍の原稿に合わせたため（コミット `2a64688`）。共通化するなら、原稿との違いとして扱う
- 直し方の案：`LIKE` のエスケープは 1 つのメソッド（たとえば `service` パッケージの package-private な static メソッド）にまとめ、つぶやき・日記の両方から呼ぶ。プロンプトのエスケープも同様。切り出しは、少なくとも文字の数え方（コードポイント）をそろえる

### 5. Javadoc が古い（低）

- 状態：対応済み
  - ✓ 対応済み（2026-10-16、コミット `223c0b0`）：`DiaryService#findDiaryForSummary` を「`{@link #findByDate}` と違い、開ける日付（登録日から今日まで）かは確かめない」に、`PostService#isEditableDate` を「つぶやきの編集・削除を受け付ける日付」（日記を開ける日付は `DiaryService#isAvailableDate` で別に判定する）に直した。コメントだけの変更なので、テストは足していない

- `DiaryService#findDiaryForSummary`（52 行目）：「`{@link #findDiary}` と違い、日付が今日・昨日かは確かめない」とあるが、`findDiary` というメソッドはない（今は `findByDate`）。また、`findByDate` が確かめる範囲は今は「登録日から今日まで」で、「今日・昨日」ではない
- `PostService#isEditableDate`（40 行目）：「編集・削除、日記の表示と生成を受け付ける日付（今日と昨日）」とあるが、日記の表示と生成は今は `DiaryService#isAvailableDate`（登録日から今日まで）で判定している。`isEditableDate` が受け持つのは、つぶやきの編集・削除だけ
- 何が起きるか：読んだ人が、日記の表示も今日と昨日だけだと誤解する。仕様（phase2-spec.md の「日付の範囲」）は「2 つの判定を混ぜない」と書いている
- 直し方の案：前者は `{@link #findByDate}` にし、「開ける日付（登録日から今日まで）かは確かめない」に直す。後者は「つぶやきの編集・削除を受け付ける日付」に直す

### 6. ログメッセージの言語が 1 か所だけ日本語（低）

- 状態：保留
  - 判断メモ（2026-10-15）：書籍の原稿のログ（`log.info("つぶやき投稿: ...")`）に合わせると決めて日本語にした。ログの言語を統一するときに見直す

- 場所：`PostService#create`（94 行目）の `"つぶやき投稿: userId=..."`。ほかのログはすべて英語（`"Post edited: ..."`・`"Post deleted: ..."` など）
- 観点：「ドメイン用語がクラス・メソッド・テンプレート・ログメッセージで一貫して使われているか」
- 何が起きるか：ログを `Post` で検索・集計すると、投稿だけが漏れる
- 補足：書籍の原稿に合わせて日本語にしたもの（コミット `d0d003f` の前後）。合わせるなら、原稿との違いとして扱う
- 直し方の案：`"Post created: ..."` に戻すか、ログの言語をどちらかに統一する方針を CLAUDE.md の「ログ」に書く

### 8. 「自分のつぶやきを取る」処理が重複（低）

- 状態：対応済み
  - ✓ 対応済み（2026-10-16、コミット `b1e647d`）：private メソッド `PostService#findOwnPost` にまとめ、`updateCategory` はそれを呼び、`findEditablePost` はそれに日付の判定を足す形にした。動作は変わらないので、既存のテスト（他人のつぶやき・一昨日のつぶやきの `updateCategory`・`edit`・`delete`）で確かめた

- 場所：`PostService#updateCategory`（147〜150 行目）と `#findEditablePost`（171〜175 行目）に、同じ `findByIdAndUserId(...).orElseThrow(() -> { log.warn(...); return new ResourceNotFoundException(...); })` がある
- 直し方の案：`findOwnPost(userId, postId)` のような private メソッドにまとめ、`findEditablePost` はそれに日付の判定を足す形にする

### 9. 本番のコードから呼ばれていないメソッド（低）

- 状態：保留
  - 判断メモ（2026-10-15）：書籍の原稿のコード（`findTodayPosts` を `findPostsByDate` に委譲する形）として残すと決めた。動作には影響しない

- 場所：`PostService#findTodayPosts`。遡り生成で `DiaryGenerationService#generateForDate` が `findPostsByDate` を直接呼ぶようになり、本番のコードからは呼ばれていない（テストからだけ使われている）
- 補足：書籍の原稿のコードとして残すと決めたもの
- 直し方の案：残すなら、Javadoc に「書籍の原稿のコードとして残している。本番のコードからは呼ばれない」と書いておく

### 10. 検索でのつぶやきと日記の取り方がそろっていない（低）

- 状態：対応済み
  - ✓ 対応済み（2026-10-16、コミット `8f43a5f`）：`PostService#searchPosts(userId, keyword, category, limit)` を足し、`SearchService` はつぶやきも日記もサービス経由で取るようにした（`PostRepository` を直接使わない）。どちらのサービスもエスケープ前のキーワードを受け取り、自分でエスケープする。テストは `PostServiceTest` に振り分けとエスケープを足し、`SearchServiceTest` は `PostService` のモックを使う形に直した

- 場所：`SearchService#search`。つぶやきは `PostRepository` を直接呼び、日記は `DiaryService#searchDiaries` を経由する
- 何が起きるか：エスケープや振り分けの場所が、つぶやきは `SearchService`、日記は `DiaryService` と分かれる（指摘 4 の `LIKE` のエスケープの重複の原因でもある）
- 直し方の案：つぶやきの検索も `PostService` にメソッドを足して経由するか、日記も `DiaryRepository` を直接呼ぶ形にそろえる

### 11. 「日記を開ける日付」の判定が 3 か所にある（中）

- 状態：未対応

- 場所
  - `DiaryService#isAvailableDate`：登録日から今日までか。日記の表示（`findByDate`）で使う
  - `DiaryGenerationService#generateForDate`：未来の日付だけを `date.isAfter(LocalDate.now(clock))` で先に自分で判定し、そのあと `isAvailableDate` も呼ぶ（未来の判定が 2 回ある）
  - `DiaryListService#summarize`：カレンダーのマスを開けるかを `!date.isBefore(registeredDate) && !date.isAfter(today)` で書き直している。月の範囲（登録月から今月まで）も、登録日と「今日」から自分で求めている
- 何が起きるか：「登録日から今日まで」は仕様が変われば動きうる業務ルール（CLAUDE.md の「入力の検証（多層防御）」の制約の置き場所）。たとえば「過去データの取り込みで登録日より前も開けるようにする」「予約投稿のために明日まで開けるようにする」と変えるとき、3 か所のどれかを直し忘れると、カレンダーではリンクになるのに開くと 404、表示はできるのに生成は断られる、のようにずれる
- 仕様：phase2-spec.md の「6. 処理の詳細 > 日付の範囲」は「この判定は service の 1 か所（`DiaryService#isAvailableDate(userId, date)`）にまとめ、日記表示・日記生成・日記一覧のカレンダーで使う」と決めている。仕様から外れている
- 直し方の案：業務ルールを `DiaryService#availableRange(userId)` の 1 か所で決め、範囲を表す record（`DiaryDateRange(first, last)`）で返す。表示・生成・カレンダーは、どれもこの範囲に「含むか」「未来か」「月の範囲」を問い合わせる。生成は、未来の日付（メッセージを付けてホームへ）と登録日より前（404）で応答を変えるので、範囲に「未来か」を聞いて振り分ける

## エラーハンドリング

### 2. 同じ日の日記を同時に生成すると 500 になる（中）

- 状態：対応済み
  - ✓ 対応済み（2026-10-16、コミット `8bb2e15`。仕様の追記はコミット `507f186`）：トランザクションの外で動く `DiaryGenerationService#generateForDate` で `DataIntegrityViolationException` を受け、WARN（例外はクラス名だけ）を残してから `saveDiary` をもう 1 回呼ぶ。1 回目のトランザクションはロールバック済みなので、2 回目は先に保存された日記の上書きになる（後から保存した方の本文が残る）。Claude API は呼び直さない。2 回目も失敗したら一意制約以外の原因なので、そのまま投げる（500）。仕様（phase1-spec.md の「二重押下の防止（サーバー側）」）の「片方は 500 になる」を、この動きに直した。テストは `DiaryGenerationServiceTest` に 2 件（やり直して成功する、2 回目も失敗したら投げる）

- 場所：`DiaryService#saveDiary`（84〜97 行目）
- 何が起きるか：別々のタブから、同じ日の「日記を生成」をほぼ同時に押すと、どちらも `findByUserIdAndDiaryDate` で「まだない」と判断して INSERT する。`diaries` には `(user_id, diary_date)` の一意制約があるので、2 件目の INSERT が `DataIntegrityViolationException` になる。この例外はどこでも受けていないので、`RequestLoggingFilter` が ERROR を出して 500 のエラー画面になる。Claude API の料金は 2 回分かかる
- 補足：仕様（phase1-spec.md の「13. やらないこと」「14. 未決事項」）は、サーバー側の二重押下の防止をしないと決めている。料金が 2 回かかることは未決事項に書いてあるが、2 件目が 500 になることは書いていない
- 直し方の案：一意制約の違反を受けて、既存の日記の上書きとしてやり直す（または `DiaryGenerationException` と同じメッセージで日記表示に戻す）。少なくとも、仕様の未決事項に「2 件目は 500 になる」を書き足す

### 7. 前提を満たさない呼び出しで黙って 0 件を返す（低）

- 状態：対応済み
  - ✓ 対応済み（2026-10-16、コミット `2cbc816`）：`SearchService#search` の先頭で、キーワードもカテゴリもないときは `IllegalArgumentException` を投げるようにした（controller はこの場合 `recentDiaries` を使うので、画面からは起きない）。テストは `SearchServiceTest` に、例外になり、リポジトリ・サービスを呼ばないことを足した

- 場所：`SearchService#search`（49〜61 行目）
- 何が起きるか：Javadoc は「キーワードもカテゴリも指定がないときは呼ばない」としているが、呼んだ場合は `searchByCategory(userId, null, ...)` になり、JPQL の `p.category = :category` が `NULL` との比較になって 0 件を返す。例外にもログにもならないので、controller の振り分けを誤って変えたときに気づきにくい
- 直し方の案：先頭で `keyword.isEmpty() && category == null` なら `IllegalArgumentException` を投げる（呼び出し側のバグとして 500 で見つかる）

### 確認したが問題がなかった点

- 業務上の失敗はすべて専用の例外で表している（`DailyPostLimitExceededException`・`NoPostsException`・`InvalidDiaryDateException`・`DiaryNotFoundException`・`DiaryMonthNotFoundException`・`ResourceNotFoundException`）。例外のメッセージは開発者向け（ID・日付・上限値）で、本文は入れていない
- Claude API の失敗の扱いが、意図して選ばれている。分類（`PostCategoryClassifier`）は `Optional.empty()` に潰し、つぶやきの保存を止めない。日記（`ClaudeService`）は `DiaryGenerationException` で伝え、前の日記を残す。どちらも仕様どおり
- `catch` している箇所（`ClaudeService`・`PostCategoryClassifier` の `AnthropicException`、`PostCategoryClassifier#parse` の `IllegalArgumentException`）は、どれも ERROR か WARN を残している（`parse` は呼び出し元が WARN を出す）。SDK の例外だけを受け、ほかの `RuntimeException` は隠していない
- 書き込みはすべてトランザクションの中。`create`・`edit` は Claude API をトランザクションの外で呼び、保存だけを `TransactionTemplate` で囲む。途中で例外が起きても部分的な書き込みは残らない（`edit` は判定を待つ間の削除でロールバックすることをテストで確かめている）
- ログのレベルは「ログ」の表どおり。業務エラーは WARN、Claude API の例外はスタックトレース付きの ERROR、正常な業務イベントは INFO。INFO 以下でスタックトレースを出している箇所はない

## セキュリティ

### 1. 改行を含む既存のつぶやきでプロンプトに偽の行を紛れ込ませられる（中）

- 状態：対応済み
  - ✓ 対応済み（2026-10-16、コミット `3c3ba46`）：`ClaudeService#buildUserMessage` で、本文の改行（`\R`）を空白に置き換えてからエスケープするようにした。入力側（フォームと `Post`）の守りと重ねて、DB に残る改行入りのつぶやきにも効く。テストは `ClaudeServiceTest` に、改行と「- [仕事] …」を含む既存のつぶやき（`Post` のコンストラクタを通らない状態を `ReflectionTestUtils` で再現）が 1 行のまま入ることを足した

- 場所：`ClaudeService#buildUserMessage`（161〜171 行目）
- 何が起きるか：日記のプロンプトは 1 行に 1 つぶやき（`- [仕事] 本文`）で並べる。新しい投稿・編集では、フォームと `Post` のコンストラクタ・`edit` が改行を拒否する（コミット `8074373`）。しかし、それより前（フェーズ 1）に保存された改行入りのつぶやきは DB に残っていて、JPA が DB から読むときはコンストラクタを通らないので、そのまま `buildUserMessage` に渡る。本文に `改行 + "- [仕事] 嘘の予定"` が入っていれば、偽のつぶやきとしてプロンプトに入る
- 補足：仕様（phase2-spec.md の「入力チェック（変更）」）は「フェーズ 1 で保存された、改行を含むつぶやきはそのまま残る」と書いているが、それがプロンプトに入ることには触れていない。`<posts>` の区切りと `<`・`>`・`&` のエスケープはできているので、区切りを壊すことはできない。影響は日記の内容が乱れることに留まる
- 直し方の案：`buildUserMessage` で本文の改行を空白に置き換える（`content.replaceAll("\\R", " ")`）。入力側の守りと重ねて、既存のデータにも効く

### 確認したが問題がなかった点

- **他人のデータ（IDOR）**：つぶやき・日記を取る・変えるクエリは、すべてユーザー ID を条件に含む（`findByIdAndUserId`・`findByUserIdAndDate`・`findByUserIdAndDiaryDate`・`searchByContent` など）。他人のつぶやきは、存在しない場合と区別せず `ResourceNotFoundException` にしている
- **SQL・LIKE**：クエリはすべてメソッド名か `@Query` + `@Param`。`LIKE` のキーワードは `!`・`%`・`_` をエスケープし（`!` を先に二重化）、クエリに `ESCAPE '!'` を付けている
- **プロンプトインジェクション**：本文はシステムプロンプトに入れず、ユーザーメッセージの区切り（`<posts>`・`<post>`）の中に、エスケープしてから入れている。分類の AI の出力は `PostCategory` と照らし合わせ、候補にない値は空の `Optional` にしている
- **ログ**：本文・検索キーワード・カテゴリの値・判定結果・API キーは出していない。Claude API の応答の中身も出さず、長さだけを出している
- **画面への漏れ**：Claude API のエラー内容や例外のスタックは、例外のメッセージにも画面向けのメッセージにも入れていない（`DiaryGenerationException` のメッセージは固定の英文。画面には controller の日本語のメッセージを出す）
- **シークレット**：API キーはサービス層に出てこない（`AnthropicConfig` が環境変数から読み、クライアントの Bean として渡す）

## よくできている点

- **責務の分け方**：Claude API の呼び出しは `ClaudeService`（`DiaryWriter`）と `PostCategoryClassifier` に閉じていて、`PostService`・`DiaryGenerationService` はそれを介して使う。リポジトリ呼び出しと API 呼び出しが直接混ざっていない
- **外部 API とトランザクション**：API の応答を待つ間は DB 接続を握らず、待つ間に変わりうる状態（投稿の上限、削除、手で直したカテゴリ）を保存の直前に確かめ直している
- **定数と enum**：しきい値は `DAILY_POST_LIMIT`・`MAX_RESULTS`・`RECENT_PAGE_SIZE`・`EXCERPT_LENGTH` などの名前付き定数、カテゴリは `PostCategory`、検索結果は sealed interface（`SearchHit`）で表している
- **時刻のテスト容易性**：「今日」はすべて注入した `Clock` から求めていて（指摘 3 のゾーンの件を除く）、テストで時刻を固定できる
- **コメント**：多くのコメントが「なぜそうするか」（料金をかけないため、DB 接続を握らないため、ログに出さない理由など）を書いている

## このレビューで見ていないもの

- サービス層以外（controller・form・domain・config・テンプレート）。書籍の手順どおり、次の段階で別にレビューする
- テストコードの中身（テストがあることは確かめたが、テストの質は見ていない）
- 実際の DB・Claude API を使った動作（コードを読んだだけ）
