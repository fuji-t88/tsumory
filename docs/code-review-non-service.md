# サービス層以外のコードレビュー

CLAUDE.md の「コードレビューの観点」（可読性・保守性、エラーハンドリング、セキュリティ）に沿って、サービス層以外をレビューした結果。サービス層は [code-review-service.md](code-review-service.md)。

- 対象（2026-10-16 時点、コミット `de93f9a`）
  - controller：`PostController`・`DiaryController`・`DiaryListController`・`SearchController`・`LoginController`・`NotFoundExceptionHandler`・`PostView`
  - form：`PostForm`・`PostEditForm`・`SearchForm`
  - domain：`Post`・`Diary`・`User`・`PostCategory`
  - config：`SecurityConfig`・`AnthropicConfig`・`ClaudeProperties`・`ClockConfig`・`CustomUserDetails`・`CustomUserDetailsService`・`RequestLoggingFilter`
  - テンプレート：`home.html`・`diary.html`・`diary-list.html`・`diaries.html`・`search.html`・`login.html`・`fragments/header.html`・`fragments/category-badge.html`・`error/500.html`
  - スクリプト：`static/js/diary-generate.js`
  - あわせて見たもの：repository（`PostRepository`・`DiaryRepository`・`UserRepository`）。他人のデータや SQL に関わるため
- 方法：コードを読んで観点と突き合わせた。エラー画面（指摘 1）だけは、読んだだけでは確かめられないので、実際のサーバー（ランダムポート）を立てる一時的なテストで応答を確かめ、確かめたあとテストは消した。コードは変えていない。指摘の重要度は次のとおり
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
    - ✓ 対応済み（2026-10-17、コミット abc1234）：投稿フォームの送信中にボタンを無効にした。static/js/post-submit.js に置いた
  ```

- 指摘を消さない。対応済み・保留になっても残し、後で「あの指摘はどう判断したか」を振り返る一次情報にする

## まとめ

| # | 重要度 | 観点 | 指摘 | 場所 | 状態 |
|---|---|---|---|---|---|
| 1 | 高 | エラーハンドリング | 404 のエラー画面（`error/404.html`）がない。404・405・400 は Spring Boot の英語の画面（Whitelabel Error Page）になる | `templates/error/` | 対応済み |
| 2 | 中 | エラーハンドリング | 投稿・編集フォームに、仕様の「送信中の表示と二重送信の防止」がない。分類を待つ数秒の間に連打すると、同じつぶやきが何件もできる | `templates/home.html` | 未対応 |
| 3 | 中 | エラーハンドリング | `GET /diary` のキーワードを form のクラスと `@Valid` ではなく、`@RequestParam` と手書きの長さチェックで受けている | `DiaryController#diaryList` | 未対応 |
| 4 | 中 | 可読性・保守性 | 投稿の上限の判定が、service と controller の 2 か所にある | `PostController#populateHomeModel` | 未対応 |
| 5 | 低 | セキュリティ | 今日のつぶやき画面に、インラインのイベントハンドラ（`onchange`・`onsubmit`）がある | `templates/home.html` | 未対応 |
| 6 | 低 | 可読性・保守性 | 本文・キーワードの文字数（100・50）が、form・テンプレート・エンティティ・controller に数字で散らばっている | `PostForm`・`PostEditForm`・`SearchForm`・`DiaryController`・`Post`・テンプレート | 未対応 |
| 7 | 低 | 可読性・保守性 | 画面のメッセージで、ドメイン用語や動作の言葉がそろっていない（「投稿」と「つぶやき」、生成なのに「表示できません」） | `PostController`・`DiaryController` | 保留 |
| 8 | 低 | 可読性・保守性 | `Post` が自分で守っている入力の決まりが、改行の禁止だけ（空・100 文字はフォームと DB だけ） | `Post` | 未対応 |
| 9 | 低 | 可読性・保守性 | controller の引数の名前と順番がメソッドごとにそろっていない | `PostController`・`DiaryController` | 未対応 |
| 10 | 低 | 可読性・保守性 | コメントが古い、または作業メモが残っている | `AnthropicConfig`・`fragments/header.html` | 未対応 |

高の指摘が 1 件ある（指摘 1。対応済み）。認証・認可、CSRF、XSS、他人のデータの絞り込み、オープンリダイレクト、ログの扱いは観点どおり（末尾の「確認したが問題がなかった点」）。

## 可読性・保守性

### 4. 投稿の上限の判定が 2 か所にある（中）

- 状態：未対応

- 場所：`PostController#populateHomeModel` の `posts.size() >= PostService.DAILY_POST_LIMIT`。service 側は `PostService#checkPostLimit` の `countPosts(...) >= DAILY_POST_LIMIT`
- 何が起きるか：今はどちらも「今日のつぶやきの件数が 16 件以上」で同じ結果になる。しかし、上限の決まりを変える（たとえば削除したつぶやきも数える、ユーザーごとに上限を変える）と、画面のフォームを無効にする判定と、実際に投稿を断る判定がずれる。フォームは使えるのに投稿すると断られる、またはその逆になる
- 観点：「メソッドの責務が単一か」。業務の決まりは service に置き、controller は結果を画面に渡すだけにする
- 直し方の案：`PostService` に `isPostLimitReached(userId, date)` のようなメソッドを足し、controller はそれを呼ぶ。`checkPostLimit` も同じメソッドを使う

### 6. 文字数が数字で散らばっている（低）

- 状態：未対応

- 場所
  - つぶやきの 100 文字：`PostForm`・`PostEditForm` の `@Size(max = 100)` と文言「100 文字以内」、`Post` の `@Column(length = 100)`、`home.html` の `maxlength="100"`（2 か所）、DB の `VARCHAR(100)`
  - キーワードの 50 文字：`SearchForm` の `@Size(max = 50)` と文言、`DiaryController` の `KEYWORD_MAX_LENGTH` と `KEYWORD_TOO_LONG_MESSAGE`、`search.html`・`diary-list.html` の `maxlength="50"`
- 観点：「マジックナンバーや裸の文字列がないか。閾値は `DAILY_POST_LIMIT` のような定数で表現されているか」
- 何が起きるか：上限を変えるとき、どれかを直し忘れると、画面では入力できるのにサーバーで弾かれる（またはその逆）
- 直し方の案：Java の中は定数にまとめる（たとえば `Post.CONTENT_MAX_LENGTH = 100`。アノテーションの属性にも定数なら使える）。テンプレートの `maxlength` は、controller から値を渡すか、少なくとも定数の Javadoc に「テンプレートの maxlength も直す」と書く。DB の長さはマイグレーションで別に直す必要がある

### 7. 画面のメッセージの言葉がそろっていない（低）

- 状態：保留
  - 判断メモ（2026-10-16）：どちらも書籍の原稿の文言に合わせたもの（`DAILY_LIMIT_MESSAGE` はコミット `d07a1da`、`FUTURE_DATE_MESSAGE` はコミット `4dd7712`）。原稿の文言を変えるかを決めてから直す

- 場所
  - `PostController.DAILY_LIMIT_MESSAGE`：「今日の**投稿**は 16 件に達しました」。ほかの画面の表示はすべて「つぶやき」（フォームの横の案内は「今日の**つぶやき**は 16 件に達しました。明日またつぶやけます。」）
  - `DiaryController.FUTURE_DATE_MESSAGE`：「未来の日付の日記は**表示**できません」。実際は日記の**生成**を断ったときに出る
- 観点：「ドメイン用語がクラス・メソッド・テンプレート・ログメッセージで一貫して使われているか」
- 直し方の案：「今日のつぶやきは 16 件までです」「未来の日付の日記は生成できません」のように、画面の言葉と動作に合わせる

### 8. `Post` が守る入力の決まりが改行だけ（低）

- 状態：未対応

- 場所：`Post` のコンストラクタと `edit`（`requireSingleLine`）
- 何が起きるか：改行の禁止はフォームと `Post` の両方で守っている（多層防御）が、空の本文と 100 文字の上限はフォーム（と DB の列の長さ）だけ。フォームを通らない経路（テストデータ、将来の API など）で 101 文字の本文が来ると、`Post` は作れてしまい、保存のときに DB のエラー（500）になる
- 直し方の案：`requireSingleLine` と同じ場所で、空でないことと 100 文字以内であることも確かめる。指摘 6 の定数を使う

### 9. controller の引数の名前と順番がそろっていない（低）

- 状態：未対応

- 場所：`PostController#create` は書籍の原稿に合わせて `(postForm, binding, principal, model, flash)`、ほかのメソッドは `(principal, id, form, bindingResult, model, redirectAttributes)`。`DiaryController` でも `flash` と `redirectAttributes` が混ざっている
- 何が起きるか：動作には影響しない。読むときに同じものが違う名前で出てくる
- 補足：`create` の形は原稿に合わせたもの。そろえるなら、原稿の形（`binding`・`flash`）に寄せるのが手戻りが少ない
- 直し方の案：`BindingResult` は `binding`、`RedirectAttributes` は `flash` にそろえる

### 10. コメントが古い、または作業メモが残っている（低）

- 状態：未対応

- 場所
  - `AnthropicConfig` の「Claude API のクライアント。ClaudeService が使う」
  - `fragments/header.html` の `<!-- ナビバーに追加 -->`（「履歴」ボタンの上）
- 何が起きるか
  - `AnthropicConfig`：今は `PostCategoryClassifier` もこのクライアントを使う（CLAUDE.md の「AI（Claude API）」は 2 つのクラスが共有すると書いている）。クライアントの設定（タイムアウトなど）を変えるとき、分類への影響を見落としやすい
  - `header.html`：何をするボタンかではなく、足したときの作業の指示が残っている。また、`<!-- -->` は HTML のコメントなので、すべての画面の HTML にそのまま出る（ほかのテンプレートは、画面に出ない Thymeleaf のコメント `<!--/* */-->` を使っている）
- 直し方の案
  - `AnthropicConfig`：「`ClaudeService`（日記）と `PostCategoryClassifier`（分類）が共有する。分類は呼び出しごとに短いタイムアウトを上書きする」に直す
  - `header.html`：「日記一覧（`/diary`）への近道」のように役割を書き、`<!--/* */-->` にする。同じファイルのログアウトのコメントも `<!--/* */-->` にそろえる

## エラーハンドリング

### 1. 404 のエラー画面がない（高）

- 状態：対応済み
  - ✓ 対応済み（2026-10-16、コミット `5d59569`）：仕様の文言で `templates/error/404.html` を作り、404・500 以外（405・400・403 など）の最後の受け皿として `templates/error.html` を足した（仕様への追記はコミット `37f08cd`）。`application.yaml` に `spring.web.error.include-*`（スタックトレース・例外・メッセージ・入力エラーを応答に含めない）を明示した。Boot 4 では `server.error.*` が非推奨（レベル error）なので `spring.web.error.*` を使った。テストは `ErrorPageTest` に 3 件足した（404 の画面の文言、状態コードごとの画面の選び分け、汎用の画面が内部情報を出さないこと）。実際のサーバーでも、404・405・403（CSRF トークンなし）・500 で Whitelabel の画面が出ず、例外のメッセージも画面に出ないことを、一時的なテストで確かめた（確かめたあと消した）

- 場所：`src/main/resources/templates/error/`（`500.html` だけがある。`404.html` は git の履歴にも一度もない）
- 何が起きるか：404・405・400 のとき、Spring Boot が既定で出す英語の画面（「Whitelabel Error Page」「This application has no explicit mapping for /error …」）になる。ヘッダーも「今日のつぶやきへ戻る」ボタンもないので、利用者は戻り方が分からない。実際のサーバーで、ログインした状態で次の応答を確かめた
  - 404：`/posts/abc/edit`（URL の値が不正）、`/diary/2000-01-01`（登録日より前の日記）、`/no-such-page`（ない URL）
  - 405：`GET /posts`、`GET /posts/1/category`（POST だけの URL を開いた。ブラウザでフォームの送信先をそのまま開くと起きる）
  - 400：`POST /posts/{id}/category` で `category` がないとき（`PostController` の Javadoc のとおり）。画面からは送れない
  - 500 は `error/500.html` が使われることも、同じ方法で確かめた
- 仕様
  - phase1-spec.md の「4. 画面 > エラー画面（`templates/error/404.html`、`500.html`）」で、404 は「404」「ページが見つかりません」「つぶやきが削除されたか、URL が間違っている可能性があります。」と「今日のつぶやきへ戻る」ボタンを出すと決めている。「8. エラー処理」の表も、URL の値の不正と範囲外の日記の日付を `templates/error/404.html` にしている。仕様にあるのに作られていない
  - CLAUDE.md の「コーディング規約 > 全般」は、画面の表示テキストを日本語で書くとしている
- 補足：`NotFoundExceptionHandler`・`DiaryNotFoundException` などは、仕様どおり 404 を返している（状態コードは正しい）。足りないのは、その 404 を描く画面だけ
- 直し方の案
  - 仕様の文言で `templates/error/404.html` を作る。ヘッダーは `500.html` と同じくサービス名だけにする
  - 405・400 など、404 以外の 4xx 用に `templates/error/4xx.html` も作る（Spring Boot は `error/<状態コード>` → `error/4xx` の順に探す）。文言は仕様にないので、仕様に足してから作る
  - テストは、`ErrorPageTest` と同じく、テスト用の controller から `error/404` を描画して文言を確かめる（MockMvc はエラー画面への切り替えを再現しないため）

### 2. 投稿・編集フォームに送信中の表示と二重送信の防止がない（中）

- 状態：未対応

- 場所：`templates/home.html` の投稿フォーム（`POST /posts`）と編集フォーム（`POST /posts/{id}/edit`）
- 何が起きるか：投稿と編集は、保存の前に Claude API でカテゴリを分類するので、応答まで数秒（最大で分類のタイムアウトの 10 秒）かかる。その間もボタンは押せるので、反応がないと思って連打すると、同じつぶやきが押した回数だけ保存される（Claude API の料金もその分かかる）。投稿の上限（16 件）にも早く達する
- 仕様：phase2-spec.md の「5. 画面 > 今日のつぶやき（変更）」の 4 で、「つぶやく」「保存」を押したらボタンを無効にし、「つぶやいています…」（編集は「保存しています…」）に変えると決めている。完成の条件にも「送信中はボタンが押せず、連打しても 1 件しか投稿されない」とある。仕様にあるのに作られていない
- 直し方の案：日記の生成と同じ作り（`static/js/diary-generate.js`）で、フォームの `submit` で処理し、2 回目以降の `submit` を止める。スクリプトは `static/js/` に置く

### 3. `GET /diary` のキーワードを form のクラスで受けていない（中）

- 状態：未対応
  - 判断メモ（2026-10-16）：`@RequestParam` で受けるのは書籍の原稿のコード（`diaryList`）に合わせたもの（コミット `485527e`）。長さのチェックは、原稿にないので足した。直すなら、原稿との違いとして扱う

- 場所：`DiaryController#diaryList`
- 何が起きるか：キーワードを `@RequestParam String keyword` で受け、長さ（50 文字）を controller の `if` で確かめている。CLAUDE.md の禁止事項「入力の受け取り」は「必ず `form/` のクラスで受け、Bean Validation を通す（`@Valid`）」としている。今は長さの上限を確かめているので実害はないが、検索画面（`SearchForm`）と決まりが 2 か所に分かれ（指摘 6）、文言も別に持っている。キーワードの決まりを足す（たとえば記号だけを禁止する）と、片方だけに入る
- 観点：「ユーザー入力に対するバリデーション（`@Valid` / Bean Validation）が漏れていないか」
- 直し方の案：`SearchForm` と同じ決まりの form（たとえば `DiaryListForm(keyword)`。または `SearchForm` の `q` を使い回す）で受け、`@Valid` と `BindingResult` で入力エラーを出す

## セキュリティ

### 5. インラインのイベントハンドラがある（低）

- 状態：未対応
  - 判断メモ（2026-10-16）：`onchange="this.form.submit()"`（カテゴリのセレクトボックス）は書籍の原稿に合わせたもの（コミット `1c89b5b`）。`onsubmit`（「日記を生成」）はフェーズ 1 からある

- 場所：`templates/home.html` の 64 行目（`onchange`）と 125 行目（`onsubmit`）
- 何が起きるか：どちらも固定の文字列で、ユーザーの入力は入っていないので、今は XSS にはならない。しかし、インラインのスクリプトがあると、将来 Content-Security-Policy（CSP）で `unsafe-inline` を禁止して XSS の被害を抑える対策が入れられない。仕様（phase2-spec.md の「今日のつぶやき（変更）」）も「スクリプトは `static/js/` に置き、インラインの `<script>` は書かない」としていて、日記表示（`diary.html`）は `diary-generate.js` に分けている
- 直し方の案：指摘 2 の送信中の処理とあわせて、`static/js/` のスクリプトに移す（`addEventListener` で付ける）

## 確認したが問題がなかった点

### 可読性・保守性

- **層の分け方**：controller は service だけを呼び、repository を直接使っていない。テンプレートにはエンティティではなく、表示用の record（`PostView`・`DiaryItem`・`SearchHit` など）を渡している
- **エンティティ**：`@Getter` だけで `@Data`・`@Setter` はない。引数なしのコンストラクタは `protected`、`@ManyToOne` は `FetchType.LAZY` を明示している。カテゴリを変える経路は `assignCategory`・`markCategorySetByUser` に限っている
- **設定値**：Claude API のモデル・タイムアウトなどは `ClaudeProperties`（`@Validated`）で受け、不正な値なら起動しない。現在時刻は `ClockConfig` の `Clock` から取る
- **テンプレートの部品**：ヘッダー（`fragments/header.html`）とカテゴリのバッジ（`fragments/category-badge.html`）は部品にして各画面から使い、メニューの選択中の表示も引数 1 つで切り替えている

### エラーハンドリング

- **専用の例外で受ける**：controller は、業務上の失敗を専用の例外の型（`ResourceNotFoundException`・`DailyPostLimitExceededException`・`InvalidDiaryDateException`・`NoPostsException`・`DiaryGenerationException`）だけで `catch` し、画面には日本語の一般的なメッセージ（controller の定数）を出している。汎用の例外の型で受けている箇所はない
- **URL の値の不正**：`NotFoundExceptionHandler` が、変換できない値（`/posts/abc/edit`、日付でない `date`、候補にないカテゴリ）をまとめて 404 にしている（状態コードは正しい。描く画面がないのは指摘 1）
- **想定外の例外**：`RequestLoggingFilter` が、リクエスト ID・ユーザー ID 付きのスタックトレースを ERROR で出し、500 を返す。500 の画面（`error/500.html`）には、問い合わせ用のリクエスト ID だけを出し、スタックトレースや SQL は出さない。実際のサーバーでも、例外のときにこの画面が使われることを確かめた
- **入力エラーの再表示**：投稿・編集・検索は form の record と `@Valid` で受け、入力値を残して再表示する。編集の入力エラーでも、編集できないつぶやきなら再表示せずに戻す
- **日記の生成中の表示**：`diary-generate.js` は `submit` で処理し（Enter キーでの送信も同じ）、2 回目以降の送信を止める。ボタンは送信が始まってから無効にし、ブラウザの「戻る」で戻ったとき（`pageshow`）は元に戻す。JavaScript が無効でも生成はできる

### セキュリティ

- **認証・認可**：`permitAll()` はログイン画面と静的リソースだけで、ほかはすべて認証必須。パスワードは `BCryptPasswordEncoder`。`CustomUserDetails` は `toString()` でパスワードのハッシュを伏せている。ログイン画面は、ユーザー名とパスワードのどちらが違うかを区別しないメッセージを出している
- **他人のデータ（IDOR）**：controller はユーザー ID を `@AuthenticationPrincipal` の `CustomUserDetails` からだけ取り、リクエストのパラメータからは受けない。repository のクエリは、すべてユーザー ID を条件に含む
- **CSRF**：CSRF 対策を無効にしていない。状態を変える操作はすべて POST で、フォームは `th:action` を使うのでトークンが入る（ログアウトも POST のフォーム）
- **XSS**：`th:utext` は使っていない。検索の強調も、区切りのリストを `th:text` で出して `<mark>` で囲む形。JS で HTML を組み立てる箇所（`innerHTML` など）もなく、`diary-generate.js` も `createElement` と文字列の `append` だけ
- **オープンリダイレクト**：リダイレクト先は固定の文字列か、`LocalDate` に変換した値から組み立てている（`/posts/{id}/category` の `date`）。ページ送りのリンクも `@{...}` のパラメータで組み立てている
- **SQL**：クエリはメソッド名か `@Query` + `@Param`。`LIKE` は `ESCAPE '!'` 付きで、キーワードは service でエスケープしてから渡す
- **ログ**：`RequestLoggingFilter` は、受け取ったリクエスト ID を安全な書式に限って引き継ぎ（ログの偽の行を防ぐ）、URI はパスだけを出してクエリ文字列（検索キーワード）を出さない
- **シークレット**：API キーは `AnthropicConfig` が `@Value("${ANTHROPIC_API_KEY}")` で受け、`ClaudeProperties` には入れていない（`toString()` で出ないように）。設定ファイルに値はない

### 仕様で未決として残しているもの（このレビューの指摘にはしない）

- ログインの試行回数の制限がない（phase1-spec.md の「14. 未決事項」）
- サーバー側の二重押下の防止がない（日記の生成。phase1-spec.md の「13. やらないこと」）。投稿・編集の画面側の防止がないのは指摘 2

## このレビューで見ていないもの

- サービス層（[code-review-service.md](code-review-service.md)）
- テストコードの中身
- 実際のブラウザでの見た目と操作、スマホ幅での表示（エラー画面の応答以外は、コードを読んだだけ）
