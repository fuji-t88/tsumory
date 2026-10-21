# DB の時刻に合わせた時計のレビュー

本番の「今」を DB の時刻に合わせる時計（`config/DatabaseClock`・`config/ClockConfig`。phase1-spec.md の「5. 処理の詳細 > 現在時刻の基準（本番は DB の時刻）」）を、資料の観点と資料の実装（`TimeConfig`・`DatabaseSyncedClock`）に照らして見た結果。ほかのレビューは [code-review-basic-auth.md](code-review-basic-auth.md) など。

- 対象（2026-10-21）：`DatabaseClock`・`ClockConfig`・`DatabaseClockTest`・`ClockConfigTest`。あわせて `Dockerfile`・`compose.yaml`（どこで有効になるかを見るため）
- 方法：観点ごとにコードとテストを照らし合わせた。守りをわざと壊してテストが捕まえるかを確かめ、開発の PostgreSQL につないで起動した
- 指摘の重要度と状態の付け方は [code-review-non-service.md](code-review-non-service.md) の「指摘の状態の管理」と同じ

## 依頼された観点

### 作り（2026-10-21）

| 観点 | 結果 | 根拠 |
|---|---|---|
| 本番（決まった設定値のとき）だけ DB 版になり、ほかはシステム時計 | 満たす | `app.clock.source` が既定の `system` ならシステム時計、`database` のときだけ `DatabaseClock`。本番のイメージ（`Dockerfile`）が `database` にし、`compose.yaml` の `app` は `system` に戻す（指摘 1） |
| 時刻を読むたびに DB に問い合わせない | 満たす | `instant()` は測った DB の時刻に経過時間を足すだけ。DB に問い合わせるのは起動時と 1 分ごとだけ |
| 起動時に取れなかったときの挙動を決めている | 満たす（fail-fast） | 最初に測れなければ起動しない。DB につながらなければ Flyway でも起動できないので、システム時計で動き続ける意味がない |
| 起動時の初回と定期の測り直しの両方がある | 満たす | 初回はコンストラクタ、測り直しは `@Scheduled`（1 分ごと） |
| スケジューラの後始末が Bean のライフサイクルに沿う | 満たす | スケジューラを自分で持たず、Spring の `@EnableScheduling` に任せる。コンテキストを閉じると予約が取り消される（テスト「閉じると定期の測り直しを止める」） |

### テスト（2026-10-21）

| 観点 | 結果 | 根拠 |
|---|---|---|
| 起動時の正常系（DB の時刻が反映される）と異常系（接続失敗） | 満たす | `DatabaseClockTest`「DBの時刻を起点に経過時間を足して返す」「最初に測れなければ作れない」。組み立てた形でも `ClockConfigTest`「databaseならDBの時刻に合わせた…」「databaseでDBの時刻を測れなければ起動しない」（指摘 2 で足した） |
| 測り直しが成功すれば追従し、失敗すれば前の値を保つ | 満たす | `DatabaseClockTest`「測り直すと新しいDBの時刻を起点にする」「測り直しに失敗したら前に測った時刻を起点に進め続ける」 |
| 止めたあとに測り直しが走らない | 満たす | `ClockConfigTest`「閉じると定期の測り直しを止める」（指摘 2 で足した）。資料の `shutdown()` に当たるものはなく、コンテキストを閉じたときに予約が消えることを確かめる |
| 外部依存（DB）をモックにして、実 DB なしで完結する | 満たす | `DatabaseClock` は DB の時刻を `Supplier<Instant>`、経過時間を `LongSupplier` で受けるので、テストは両方を差し替える。`ClockConfigTest` は `JdbcTemplate` をモックにし、SQL が `SELECT clock_timestamp()` であることも確かめる |

## まとめ

| # | 重要度 | 観点 | 指摘 | 場所 | 状態 |
|---|---|---|---|---|---|
| 1 | 低 | 設計 | `docker compose --profile deploy` の `app` も DB の時刻になっていた | `Dockerfile`・`compose.yaml` | 対応済み |
| 2 | 低 | テスト | 組み立てた形での起動時の接続失敗と、閉じたあとに測り直しが走らないことをテストしていなかった | `ClockConfigTest` | 対応済み |
| 3 | 中 | 正しさ | 経過時間を壁時計で測っていたので、OS の時計が飛ぶと返す時刻も飛んだ | `DatabaseClock` | 対応済み |
| 4 | 情報 | テスト | 実際の PostgreSQL での `clock_timestamp()` の読み取り（`OffsetDateTime` への変換）は自動テストで確かめていない | `ClockConfig` | 保留 |

### 1. Docker Compose でも DB の時刻になっていた（低）

- 状態：対応済み
  - ✓ 対応済み（2026-10-21、コミット `79496de`・`15bf926`・`564294f`）：`compose.yaml` の `app` に `APP_CLOCK_SOURCE: system` を足した。はじめは、`deploy` プロファイルを本番相当の確かめに使うので DB の時刻のままにしていたが、観点（DB 版は本番だけ）に揃えた。手元で DB の時刻を試すときは `docker compose --profile deploy run -e APP_CLOCK_SOURCE=database app`

### 2. 起動時の接続失敗と後始末のテストがなかった（低）

- 状態：対応済み
  - ✓ 対応済み（2026-10-21、コミット `d6854a7`）：`ClockConfigTest` に 2 件足した（「databaseでDBの時刻を測れなければ起動しない」「閉じると定期の測り直しを止める」）。`ClockConfig` を「測れなければ黙ってシステム時計に戻す」形に壊すと、1 件目が落ちることを確かめた。2 件目は Spring の動き（閉じると予約を取り消す）を確かめるもので、こちらのコードに壊せる守りはない。`DatabaseClock` が自分でスケジューラを持つように変わったら、このテストでは後始末の漏れを捕まえられないので、そのときは止め方のテストを書き直す

### 3. 壁時計が飛ぶと返す時刻も飛んだ（中）

- 状態：対応済み
  - ✓ 対応済み（2026-10-21、コミット `0f9055a`・`81b352e`）：資料と同じく、測った DB の時刻と、そのときの `System.nanoTime` を組（`Sample`）で持ち、経過時間を `nanoTime` で足す形にした。直す前の作りで、壁時計を 1 時間飛ばすと返す時刻も 1 時間飛ぶ（期待 15:00:03、実際 16:00:03）ことを確かめた
- 何が起きたか：はじめは「壁時計 + DB との差」を返していた。仕様書の理由にはゆっくり進むずれのことしか書いておらず、NTP の補正や手動の変更で壁時計が飛ぶ場合を見落としていた

### 4. 実際の PostgreSQL での読み取りを自動テストしていない（情報）

- 状態：保留
  - 判断メモ（2026-10-21）：今は直さない。テストは `JdbcTemplate` をモックにしているので、PostgreSQL の JDBC ドライバが `clock_timestamp()` を `OffsetDateTime` で返せるかは確かめていない。開発の DB につないで起動し、`Clock source: database (offset from system clock=0ms)` が出ることを手で確かめた
  - 見直す条件：Testcontainers を入れて、DB を使う結合テストを書けるようになったとき（phase1-spec.md の「14. 未決事項」）

## 資料と違えたところ（2026-10-21）

| 資料 | このプロジェクト | 理由 | 見直す条件 |
|---|---|---|---|
| 設定 `tsumory.clock.source`、`@ConditionalOnProperty` で Bean を書き分ける | 設定 `app.clock.source`、1 つの `@Bean` の `switch` | `app.gateway.*` と名前をそろえた。決まっていない値（`databse` など）を、理由の分かるメッセージで起動エラーにするため（`GatewayAuthFilter` で `@ConditionalOnProperty` を使わなかったのと同じ理由） | 切り替え先が増えて `switch` が読みにくくなったとき |
| 自分の `ScheduledExecutorService` を持ち、`destroyMethod = "shutdown"` で止める | Spring の `@Scheduled` に任せ、自分では何も持たない | CLAUDE.md の禁止事項「自前のスレッドプール」。止める責任を Spring のライフサイクルに寄せ、`destroyMethod` の書き忘れが起きないようにする | 測り直しの間隔を動かしながら変えるなど、Spring の予約では足りなくなったとき |
| 測り直しの失敗を空の `catch` で握りつぶす | WARN を出して、前の値で進め続ける | CLAUDE.md の禁止事項「例外を握りつぶす `catch`」。続けて失敗していることに、ログで気づけるようにする | なし |
| `System.nanoTime()` と `DataSource` を中で直接使う | `LongSupplier`（本番は `System::nanoTime`）と `Supplier<Instant>`（本番は `JdbcTemplate`）を外から渡す | 時刻と DB の答えをテストで決められるようにするため。`Connection`・`PreparedStatement`・`ResultSet` の連鎖をモックにせずに済む | なし |

## テストの書き方（2026-10-21）

資料の `DatabaseSyncedClock` は、DB の時刻の取得から経過時間の足し込みまでを 1 つのクラスに持ち、`System.nanoTime()` と `DataSource` を中で直接使う。外から値を差し込む場所が少ないので、資料のテストは測り直しのスケジューラを実際に動かし、実時間で様子を見る書き方になっている。設計がテストの書き方を決めた例で、このプロジェクトの設計からは、違う書き方が出てくる。

| 資料が挙げる分け方 | このプロジェクト |
|---|---|
| 設定 | `ClockConfig`（`app.clock.source` で切り替え、DB への問い合わせを組み立てる） |
| 経過時間を足す時計 | `DatabaseClock#instant` と `Sample`（DB の時刻 + `nanoTime` の経過） |
| DB と同期する処理 | `DatabaseClock#sync`。問い合わせそのものは、`ClockConfig` が渡す `Supplier<Instant>` |
| 基準の時計を DI で受け取る | 経過時間の時計を `LongSupplier` で受け取る（本番は `System::nanoTime`） |

- 同期と経過時間の計算は 1 つのクラスのままにした。そのかわり、外の世界（DB の答えと経過時間）は両方とも引数で受ける。クラスを 3 つに分けなくても、差し込む場所は作れる
- そのため、テストは値を決めて差し込み、結果を見る書き方になった。第 6 章のサービス層のテスト（`Clock.fixed` とモック）と同じ考え方で、実時間を待たず、実 DB も使わず、毎回同じ結果になる。日付の変わり目、往復の補正、壁時計が飛ぶ場合を、決まった値で確かめられる
- 資料が死角として挙げる異常系は、どちらもテストにある
  - 起動時に測れない：`DatabaseClockTest`「最初に測れなければ作れない」、`ClockConfigTest`「databaseでDBの時刻を測れなければ起動しない」
  - 測り直しに失敗する：`DatabaseClockTest`「測り直しに失敗したら前に測った時刻を起点に進め続ける」。WARN を出して握りつぶさないことは、わざと壊して確かめた
- 値を差し込む書き方では、I/O の部分が 2 つ残る
  1. 実際の PostgreSQL で `clock_timestamp()` を `OffsetDateTime` で読めるか：指摘 4（保留）。開発の DB で起動して、手で確かめた
  2. 1 分ごとに本当に `sync` が呼ばれるか：テストで確かめているのは、予約が 1 件登録されることと、閉じると消えることまで。実際に 1 分たって呼ばれるところは見ていない
     - 判断メモ：実時間で待つテストは書かない。呼び出すのは Spring で、テストが 1 分以上かかり、落ちるかどうかも安定しないため。本番では、DEBUG にすれば `Database clock synced` のログで確かめられる
     - 見直す条件：測り直しの間隔をこちらで決める作り（自分で予約する、間隔を設定で変える、など）に変えたとき

## よくできている点

- **差し替える場所が 1 か所**：service・controller・エンティティは注入された `Clock` を使うだけで、どの時計かを知らない。今回も、それらは 1 行も変えずに本番の時計を差し替えられた（CLAUDE.md の「時刻の取得」）
- **外から時刻を決められる**：DB の答えと経過時間を引数で受けるので、資料が「差し替えポイントが見えない」と書いた形にならず、日付の変わり目・往復の補正・壁時計が飛ぶ場合を、実 DB なしで決まった値で確かめられる
- **設定の誤りで起動を止める**：決まっていない設定値と、起動時に DB の時刻を測れないことを、どちらも起動エラーにする。黙ってシステム時計で動き続け、そろえたつもりでずれる事故を防ぐ

## 数字

| 測ったもの | `7c793b6`（作った時点） | `81b352e`（指摘 3 の対応後） | `d6854a7`（指摘 2 の対応後） |
|---|---|---|---|
| テストの件数（全体） | 406（すべて成功） | 406（すべて成功） | 408（すべて成功） |
| `DatabaseClockTest`・`ClockConfigTest` の件数 | 9・3 | 9・3 | 9・5 |
| わざと壊した守りのうち、テストが捕まえた数 | 9 / 9 | 10 / 10 | 11 / 11（`ClockConfig` で黙ってシステム時計に戻す形を足した） |
