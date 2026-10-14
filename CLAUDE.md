# tsumory

ユーザーが投稿した「つぶやき」を、AI（Claude）が日記にまとめるWebサービス。

## 仕様書

仕様は `docs/` にある。入口は [docs/spec.md](docs/spec.md)（各仕様書の役割を示す索引）。

- **何を作るか**（コンセプト、機能、画面、データ、処理、AI 呼び出しの設定）は仕様書に書き、仕様書を正とする。このファイルには書かない
- **どう作るか**（技術スタック、開発環境、構成、規約、禁止事項）はこのファイルに書く。仕様書には書かず、必要なら「CLAUDE.md の〜に従う」と参照する
- 仕様書とこのファイルが食い違っていたら、実装を始める前にどちらが正しいかを確かめる

## テーブル概要

全体像だけを示す。列の定義・制約・ER 図は [docs/phase1-spec.md](docs/phase1-spec.md) の「2. データベース」、エンティティの振る舞いは「3. ドメインモデル」を正とする。

| テーブル | エンティティ | 概要 |
|---|---|---|
| `users` | `User` | ログインするユーザー |
| `posts` | `Post` | つぶやき。ユーザーごとに何件でも |
| `diaries` | `Diary` | その日のつぶやきから AI が書いた日記。1 ユーザー 1 日 1 本 |

- つぶやきのクラス名は `Post` にする（`Tweet` などにしない）

## 技術スタック

- Java 21 / Spring Boot 4.1.1 / Gradle（Kotlin DSL）
- Web: Spring MVC + Thymeleaf（`thymeleaf-extras-springsecurity6`）、Bootstrap 5（WebJars）
- 認証: Spring Security
- DB: PostgreSQL 18 / Spring Data JPA / Flyway
- AI: Anthropic Java SDK（`com.anthropic:anthropic-java`）
- その他: Lombok、Bean Validation、仮想スレッド有効（`spring.threads.virtual.enabled`）

## 開発環境

- `./gradlew bootRun` で起動すると、Spring Boot の Docker Compose 連携で `compose.yaml` のコンテナも自動で起動する（Docker Desktop の起動が必要）
  - `db`: PostgreSQL。`localhost:5432`、DB 名・ユーザー名 `tsumory`、パスワード `secret`
  - `adminer`: DB 管理画面。http://localhost:8081 （サーバー欄は `db`）
- アプリは http://localhost:8080
- DB の接続情報は Docker Compose 連携が自動で設定するので、`application.yaml` には書かない
- データはボリューム `postgres-data` に永続化している。ボリューム名を変えるとデータが消えるので変えない
- Claude API のキーは、プロジェクト直下の `.env` に `ANTHROPIC_API_KEY=sk-ant-...` と書く（`.gitignore` 済み。コミットしない）
  - `application.yaml` の `spring.config.import: optional:file:.env[.properties]` で、Spring Boot の標準機能として読む。`.env` を読むためだけのライブラリ（dotenv 系など）は追加しない
  - プロパティファイルとして読むので、`export` や値を囲む引用符は書かない（引用符も値に含まれてしまう）
  - OS の環境変数が同じ名前で設定されていれば、そちらが優先される。本番は `.env` を置かず、OS の環境変数で渡す

## コマンド

- `./gradlew bootRun` — 起動
- `./gradlew build` — ビルドとテスト
- `./gradlew test` — テストのみ
- `./gradlew spotlessApply` — 整形

## ディレクトリ構成

```
tsumory/
├── compose.yaml                  # 開発用コンテナ（db, adminer）
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/com/example/tsumory/
    │   │   ├── TsumoryApplication.java
    │   │   ├── config/           # 設定クラス（SecurityConfig など）
    │   │   ├── controller/       # 画面のリクエストを受ける
    │   │   ├── service/          # 業務ロジック・トランザクション、AI 連携、例外
    │   │   ├── repository/       # DB アクセス（Spring Data JPA）
    │   │   ├── domain/           # ドメインモデル全般（JPA エンティティなど）
    │   │   └── form/             # Thymeleaf のフォーム入力（Bean Validation 付き）
    │   └── resources/
    │       ├── application.yaml
    │       ├── db/migration/     # Flyway のマイグレーション（V1__xxx.sql …）
    │       ├── templates/        # Thymeleaf。画面が複数ある機能はサブディレクトリを切る（diary/ など）
    │       └── static/           # 独自の CSS・JS・画像（Bootstrap は WebJars から読むので置かない）
    └── test/java/com/example/tsumory/   # main と同じパッケージ構成
```

### パッケージの分け方

- **層ごとに分ける（package by layer）**。`post/`・`diary/` のような機能ごとのパッケージは作らない
- クラスは役割に応じて次のパッケージに置き、名前の付け方をそろえる（つぶやき `Post` の例）

  | パッケージ | 役割 | クラス名 |
  |---|---|---|
  | `controller` | 画面のリクエストを受ける | `PostController` |
  | `controller` | 日記一覧・検索の画面のリクエストを受ける（フェーズ 2） | `DiaryListController`、`SearchController` |
  | `service` | 業務ロジック・トランザクション | `PostService` |
  | `service` | 日記を書く流れの組み立て（つぶやき集め → AI → 保存） | `DiaryGenerationService` |
  | `service` | AI 連携のインターフェース | `DiaryWriter` |
  | `service` | AI 連携（Claude API の呼び出し。`DiaryWriter` の実装） | `ClaudeService` |
  | `service` | つぶやきのカテゴリ分類の組み立て（イベントを受け → AI → 保存。フェーズ 2） | `PostClassificationService` |
  | `service` | カテゴリ分類の AI 連携のインターフェース（フェーズ 2） | `PostClassifier` |
  | `service` | カテゴリ分類の AI 連携（Claude API の呼び出し。`PostClassifier` の実装。フェーズ 2） | `ClaudePostClassifier` |
  | `service` | カテゴリ分類の仮実装（Claude API を呼ばない。`PostClassifier` の実装。Claude につないだら削除する。フェーズ 2） | `StubPostClassifier` |
  | `service` | 例外 | `ResourceNotFoundException` |
  | `repository` | DB アクセス（Spring Data JPA） | `PostRepository` |
  | `domain` | ドメインモデル（エンティティなど） | `Post` |
  | `form` | Thymeleaf のフォーム入力（Bean Validation 付き） | `PostForm`、`SearchForm` |
  | `config` | 設定 | `SecurityConfig` |
  | `config` | 非同期処理の設定（`@EnableAsync`、MDC を引き継ぐ `TaskDecorator`。フェーズ 2） | `AsyncConfig` |
  | `config` | `application.yaml` の設定値を受ける record（`@ConfigurationProperties` + `@Validated`） | `ClaudeProperties` |
  | `config` | リクエストごとにリクエスト ID とユーザー ID をログに付け、リクエストの結果を記録する（`OncePerRequestFilter`） | `RequestLoggingFilter` |
  | `config` | ログイン中のユーザー（`UserDetails` を実装する record。`@AuthenticationPrincipal` で受け、`userId()` を service に渡す） | `CustomUserDetails` |
  | `config` | ログイン時に DB からユーザーを探し、`CustomUserDetails` に詰めて返す（`UserDetailsService` の実装） | `CustomUserDetailsService` |

- パッケージは上の 6 つだけにする。`ai/`・`client/`・`exception/`・`entity/`・`dto/`・`common/`・`util/` などは作らない
- AI 連携と例外は `service/` にまとめる
- 依存の向きは controller → service → repository。controller から repository を直接呼ばない
- ドメインモデルを画面にそのまま渡さず、入力は `form/` のクラスで受ける
- Claude API（Anthropic SDK）は `service/` の AI 連携クラスからだけ使う

## コーディング規約

### 全般

- 整形は Spotless で統一する。Java は palantir-java-format（4 スペース、120 桁）、`*.gradle.kts` は ktlint
- ローカルでは `compileJava` の前に `spotlessApply` が自動で走る。環境変数 `CI` があるときは整形せず `spotlessCheck` で失敗させる
- DB スキーマの変更は Flyway のマイグレーション（`src/main/resources/db/migration/V<番号>__<説明>.sql`）で行い、JPA の `ddl-auto` には頼らない
  - 一度適用したマイグレーションファイル（開発用 DB に適用しただけのものも含む）は編集しない。列の追加・型の変更・データの修正なども、番号を 1 つ進めた新しいバージョン（`V2__add_xxx.sql` など）を追加して行う
  - 適用済みのファイルを編集すると、Flyway のチェックサム検証で起動時にエラーになり、環境ごとにスキーマがずれる原因にもなる
  - まだどこにも適用していないファイルだけは、直接書き換えてよい
- 追加する依存のうち、Spring Boot が管理していないもの（Anthropic SDK、Bootstrap など）はバージョンを明記する
- 画面の表示テキスト（見出し・ボタン・リンク・メッセージ・プレースホルダー・タブのタイトル）は日本語で書く。日付は `yyyy年M月d日（E）`、時刻は `HH:mm`（日本時間）で出す。サービス名「Tsumory」と URL はそのまま

### Java 21 の機能を積極的に使う

- **record**: 不変の値の受け渡し（service 間の結果、AI の生成結果など）は record で書く。`form/` のクラスも record で書く（Spring MVC はコンストラクタで値を入れ、Thymeleaf の `th:field` も record の値を読める）。JPA エンティティは record にしない（JPA が引数なしのコンストラクタと setter を前提にするため）
- **switch 式とパターンマッチング**: 分岐は `switch` 式で書き、`instanceof` は型パターン（`if (x instanceof Post p)`）を使う。record パターン（`case Result(var a, var b) ->`）も使ってよい
- **sealed interface**: 取りうる結果が決まっているもの（例: AI の結果を種類ごとに持つ値など）は sealed interface + record で表し、`switch` で漏れなく扱う。`default` を書かずにコンパイラに網羅性を確認させる。ただし日記の生成は、`DiaryWriter`・`DiaryGenerationService` が日記の本文や `Diary` だけを返し、つぶやきなし・AI 呼び出しの失敗は例外（`NoPostsException`・`DiaryGenerationException`）で知らせる。controller が `try`/`catch` で受けてメッセージを出す
- **テキストブロック**: Claude へのプロンプトや複数行の SQL は `"""` で書く
- **`var`**: 右辺から型が明らかなローカル変数に使う。型が読み取れない場合は書く
- **Sequenced Collections**: 先頭・末尾は `getFirst()` / `getLast()` / `reversed()` を使い、`get(0)` や `get(size() - 1)` は書かない
- **仮想スレッド**: 有効にしてあるので、ブロッキング I/O（DB、Claude API）は普通に同期で書く。並行処理は `Executors.newVirtualThreadPerTaskExecutor()` を使う
  - リクエストの応答を待たせずに後から処理するとき（フェーズ 2 のカテゴリ分類など）は、`@Async` を使う。実行は Spring Boot の既定のタスク実行器（`spring.threads.virtual.enabled: true` で仮想スレッドになる）に任せ、`@Async` 用に自前のスレッドプールを定義しない
  - トランザクションの確定後に動かす処理は `@TransactionalEventListener(phase = AFTER_COMMIT)` で受ける（確定前に別スレッドから読むと、まだ保存されていないため）
  - 別スレッドには MDC（`requestId`・`userId`）を引き継ぐ。`config/AsyncConfig` の `TaskDecorator` で、呼び出し元の MDC を写してから実行し、終わったら消す
- **日時**: `java.time` を使う。日記の日付は `LocalDate`、日時は `Instant` か `OffsetDateTime`。「今日」は `ZoneId.of("Asia/Tokyo")` を明示して求める（時刻の取り方は下の「時刻の取得」）
- **Optional**: 見つからない可能性がある 1 件の戻り値は `Optional` にする。フィールドや引数には使わない

### 時刻の取得

- 現在時刻は、注入した `Clock`（`config/ClockConfig` の Bean。`Asia/Tokyo`）から取得する。`Instant.now()`・`LocalDate.now()`・`LocalDateTime.now()` などを引数なしで直接呼ばない（`clock.instant()`、`LocalDate.now(clock)` と書く）
- エンティティのように Bean でないオブジェクトには `Clock` を注入しない。サービス層で求めた `Instant` を引数で渡す（例: `new Post(user, content, clock.instant())`、`post.edit(content, clock.instant())`）
- 理由：テストで `Clock.fixed(...)` を渡せば時刻を固定でき、結果が毎回同じになる。タイムゾーンの基準も `Clock` の 1 か所にまとまり、本番用の `Clock` に差し替えるときもコードを変えずに済む

### Spring・Lombok

- DI はコンストラクタインジェクション（`@RequiredArgsConstructor` + `private final` フィールド）
- `@Transactional` は service に付ける。クラスに `@Transactional(readOnly = true)` を付けて既定を読み取り専用にし、状態を変えるメソッドだけに `@Transactional` を付けて上書きする（例: `PostService`）。外部 API を呼ぶメソッドは `@Transactional(propagation = Propagation.NOT_SUPPORTED)` にし、API の応答を待つ間はトランザクションを張らない（例: `DiaryGenerationService#generateToday`）
- エンティティには `@Getter` と必要な `@Setter` だけを付ける。`@Data`・`@EqualsAndHashCode`・`@ToString` は付けない（遅延ロードや循環参照で問題が起きるため）
- エンティティの引数なしコンストラクタは `@NoArgsConstructor(access = AccessLevel.PROTECTED)` にする（JPA 用。アプリのコードからは、必須の値を受け取るコンストラクタで作る）
- エンティティの関連（`@ManyToOne` など）は `fetch = FetchType.LAZY` を明示する（`@ManyToOne` の既定は EAGER で、一覧を取得すると関連先ごとに SELECT が発行される N+1 問題の原因になるため）

### ログ

- ログは SLF4J（Lombok の `@Slf4j`）で出す。本番で障害の原因を追えるよう、service の状態を変える処理と外部 API の呼び出しには記録を残す
- 出してよいのは、個人情報を含まないメタ情報だけ（ユーザー ID、つぶやき・日記の ID、日付、件数、本文の文字数、結果の種類、処理時間、Claude API のステータスコード・リクエスト ID・メッセージ ID・トークン数）。出してはいけないものは禁止事項の「ログ」
- 例外はメッセージを出さず、クラス名だけを出す（SQL や API の応答が含まれることがあるため）
  - 例外: Claude API の呼び出しが例外で失敗したとき（ネットワーク障害・レート制限・タイムアウト・API のエラー）は、原因を調べられるよう、例外を渡してスタックトレース付きで出す（`log.error("...", e)`）。SDK の例外のメッセージには API のエラー応答（種類と説明）が入る。プロンプトやつぶやきの本文をこちらから例外やログに入れることはしない
- ログレベルは次のとおり使い分ける

  | レベル | 使う場面 | 例 |
  |---|---|---|
  | ERROR | システムが正常に動いておらず、すぐ対応が必要なとき。正常な業務処理には使わない（アラートが形骸化するため） | DB に接続できない、Claude API の呼び出しが例外で失敗した（外部 API が応答しない。エラー・タイムアウト・レート制限） |
  | WARN | 処理は続けられたが、繰り返すなら調べるべきとき | 他人のつぶやきへのアクセスの試行、Claude API の応答は返ったが日記にできない（拒否・途中で止まった・本文が空） |
  | INFO | 正常な業務イベント | つぶやきの投稿・編集・削除、日記の生成・保存 |
  | DEBUG | 開発時の調査用。本番では出さない | 変数の中身 |

- 想定外の例外は service で受け止めて ERROR を出さず、そのまま投げる（`RequestLoggingFilter` がリクエスト ID・ユーザー ID 付きの ERROR で記録し、500 を返す）

#### リクエストの追跡

本番で障害が起きたとき、ログだけでどのユーザーのどのリクエストかを特定できるようにする。

- `config/RequestLoggingFilter`（`OncePerRequestFilter` を継承し、1 リクエストで 1 回だけ動かす）が、リクエストごとに SLF4J の MDC へ次の 2 つを入れる
  - `requestId`：リクエストヘッダー `X-Request-Id` が安全な書式（英数字と `.`・`_`・`-`、64 文字以内。定数 `SAFE_REQUEST_ID`）なら引き継ぎ、ヘッダーがないか書式に合わなければ UUID を作る（受け取った値をそのまま使うと、改行などで偽のログ行を紛れ込ませられるため）。応答ヘッダー `X-Request-Id` で同じ値を返し、クライアント・プロキシ・アプリのログを同じ ID でたどれるようにする。500 のエラー画面にも出せるよう、リクエスト属性 `requestId` にも入れる（エラー画面を描くときには MDC はもう消えているが、リクエスト属性は残っている）。テンプレートでは `${requestId}` で読む
  - `userId`：ログイン中のユーザー ID（`CustomUserDetails#userId`）。`Authentication` が `null` のとき（未ログイン）や、principal が `CustomUserDetails` でないときは付けない。判定は `instanceof` のパターンマッチングで書く
- `finally` で、このフィルターが入れたキーだけを `MDC.remove()` で消す。消し忘れると、スレッドを使い回す環境で前のリクエストのユーザー情報が次のリクエストのログに混ざる。`MDC.clear()` は、同じスレッドでほかの部品が入れたキーまで消すので使わない
- `application.yaml` の `logging.pattern.level` を `"%5p [req=%X{requestId:-} user=%X{userId:-}]"` にして、すべてのログ行のレベルの直後に `[req=… user=…]` を付ける。各ログのメッセージに `userId` を書かなくても、ユーザーとリクエストが分かる
  - `logging.pattern.console` は書き換えない（Spring Boot の標準の形式（日時・スレッド名・ロガー名）を活かし、変えるのはレベルの列だけにするため）
  - `logging.pattern.correlation` は使わない（Spring Boot が分散トレーシングの `traceId`・`spanId` を出すための場所なので、空けておく）
  - `%X{…:-}` は、値がないとき（未ログインなど）に空を出す書き方。未ログインのリクエストでは `user=` が空のまま出る
- フィルターは `@Component` にし、`@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1)` で Spring Security のフィルターの直後に動かす（Spring Security がログイン中のユーザーを `SecurityContextHolder` に入れたあとでないと、ユーザー ID を取れないため）。順序は数値で書かず、定数からの相対で指定する（Spring 側の基準が変わっても追従するため）。`SecurityConfig` のチェーンには入れない（2 か所に登録すると順序が分かりにくくなるため）
  - Spring Security が自分で応答を返すリクエスト（`POST /login`・`POST /logout`、未ログインでのログイン画面へのリダイレクト）はこのフィルターまで届かないので、リクエスト ID は付かない
- リクエストの終わりに `request completed: method, uri, status, elapsedMs` を INFO で出す。`uri` はパスだけで、クエリ文字列は出さない。静的リソース（`/css/**`・`/js/**`・`/webjars/**`）は対象にしない
- 処理されなかった例外は、このフィルターがスタックトレース付きの ERROR で出してから 500 を返す（このときはまだ MDC が残っているため、例外のログにもリクエスト ID とユーザー ID が付く）
- 仮想スレッドでも、1 つのリクエストは 1 つのスレッドで処理されるので MDC はそのまま使える。自分で別スレッドに処理を渡すときは、MDC を引き継ぐ

## 禁止事項

### 実装

- `@Autowired` によるフィールドインジェクション
- `System.out.println` / `printStackTrace()` によるログ出力
- 例外を握りつぶす `catch`（何もしない、ログだけ出して処理を続ける）。扱えない例外はそのまま投げる
- `null` のコレクションを返すこと（空のコレクションを返す）
- 仮想スレッド上でブロッキング I/O を `synchronized` で囲むこと（Java 21 ではキャリアスレッドを占有する）。排他が必要なら `ReentrantLock` を使う
- 自前のスレッドプール（`Executors.newFixedThreadPool` など）や `new Thread()`
- 適用済みの Flyway マイグレーションファイルの書き換え（「コーディング規約 > 全般」のマイグレーションの方針を参照）
- `spring.jpa.hibernate.ddl-auto` を `update` / `create` にすること

### セキュリティ

- **XSS**: ユーザーの入力や AI の生成結果を `th:utext` で出力しない。常に `th:text`（エスケープあり）を使う
- **SQL インジェクション**: SQL や JPQL を文字列連結で組み立てない。Spring Data のメソッド名クエリか、`@Query` + バインドパラメータを使う
- **CSRF**: CSRF 対策を無効にしない（`csrf.disable()` 禁止）。フォームは `th:action` を使い、トークンを自動で埋め込ませる
- **パスワード**: 平文やハッシュ化なしで保存しない。`BCryptPasswordEncoder` でハッシュ化する。初期ユーザーのマイグレーションにも、平文ではなく BCrypt のハッシュ値を書く
- **他人のデータ（IDOR）**: つぶやきや日記を ID だけで取得しない。必ずログイン中のユーザーを条件に含める（例: `findByIdAndUserId(id, userId)`）。他人のデータにアクセスされた場合は、存在しない場合と区別せずに扱う。つぶやきは service が `ResourceNotFoundException` を投げ、controller が受けて「つぶやきが見つかりませんでした」のフラッシュメッセージを付けて `/` にリダイレクトする
- **認可**: `permitAll()` はログイン画面と静的リソース（`/webjars/**` など）だけ。それ以外は認証必須にする
- **秘密情報**: API キーやパスワードをコード・設定ファイル・コミットに含めない。`.env` などはコミットしない
- **ログ**: パスワード、API キー、セッション ID をログに出さない。つぶやきと日記の本文も個人的な内容なのでログに出さない
  - 検索キーワードと、つぶやきのカテゴリ分類の結果（どのカテゴリになったか）も出さない。どちらもつぶやきや日記の内容を推測できるため（フェーズ 2）
  - 例外: 開発中は、`application.yaml` の `org.hibernate.orm.jdbc.bind: TRACE` で SQL のバインドパラメータの値を出してよい。本文が含まれるので、本番デプロイ時は必ず無効化する
- **エラー表示**: スタックトレースや SQL などの内部情報を画面に出さない
- **入力の受け取り**: リクエストを直接エンティティにバインドしない。必ず `form/` のクラスで受け、Bean Validation を通す（`@Valid`）
- **プロンプトインジェクション**: つぶやきの本文をシステムプロンプトに埋め込まない。ユーザーメッセージ側にデータとして渡し、本文の中の指示に従わないようシステムプロンプトで指示する。AI の出力は表示用の文章としてだけ扱い、その内容で処理を分岐させない
  - 例外: カテゴリ分類（フェーズ 2）の出力は、決められた候補の値（`PostCategory`）と照らし合わせた場合に限り、保存する値として使ってよい。候補にない値や読めない出力は使わずに捨てる。DB の検査制約でも同じ値に限る
- **リダイレクト**: リクエストパラメータの URL にそのままリダイレクトしない（オープンリダイレクト）

## AI（Claude API）

- Anthropic Java SDK を使い、HTTP を直接呼ばない
- API キーは環境変数 `ANTHROPIC_API_KEY` から読む。コードや設定ファイルに書かない
  - `AnthropicConfig` で `@Value("${ANTHROPIC_API_KEY}")` として受け、ビルダーの `apiKey(...)` に渡す。SDK の `fromEnv()` は `System.getenv()` を直接読み、Spring の `Environment`（OS の環境変数や `.env` など）を通らないので使わない
  - キーが設定されていなければ、起動時にエラーになる
  - API キーは `ClaudeProperties` に入れない（record の `toString()` で値が出てしまうため）
- モデル・タイムアウト・リトライなどの呼び出しの設定とプロンプトは、[docs/phase1-spec.md](docs/phase1-spec.md) の「6. AI 連携」（日記）と [docs/phase2-spec.md](docs/phase2-spec.md) の「7. AI 連携（カテゴリ分類）」に従う
- Claude API を呼ぶクラスは次の 2 つ。どちらも `AnthropicConfig`（`@Configuration`）のクライアントの Bean を共有する
  - `ClaudeService`（`@Service`）：日記を書く
  - `ClaudePostClassifier`（`@Service`）：つぶやきのカテゴリを分類する（フェーズ 2）
- モデル・effort・`max_tokens`・タイムアウトは `application.yaml` に書き、`ClaudeProperties` で受ける。コードに定数で書かない
  - 日記：`anthropic.*`（`model`・`effort`・`max-tokens`。`timeout` はクライアント全体で共有）
  - カテゴリ分類：`anthropic.classification.*`（`ClaudeProperties` の入れ子の record `Classification`）
- 起動前に環境変数 `ANTHROPIC_API_KEY` を設定する

## 注意点

- Spring Security が既定で全 URL に認証をかける。`/webjars/**` や静的リソースは、ログイン前でも読めるように許可が必要
- Thymeleaf から Bootstrap を読むときはバージョンなしのパスを使う（`webjars-locator-lite` が解決する）
  - `@{/webjars/bootstrap/css/bootstrap.min.css}`
  - `@{/webjars/bootstrap/js/bootstrap.bundle.min.js}`

## Git

- ブランチは `main`。リモートは https://github.com/fuji-t88/tsumory
- コミットメッセージは英語。変更の種類ごとにコミットを分ける
