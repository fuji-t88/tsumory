# tsumory

ユーザーが投稿した「つぶやき」を、AI（Claude）が日記にまとめるWebサービス。

## ドメインモデル

```
User 1 ── * Post    （ユーザーはつぶやきを何件でも投稿できる）
User 1 ── * Diary   （日記はユーザーごとに 1 日 1 本）
Post * ──▶ 1 Diary  （その日のつぶやきから AI が日記を生成する）
```

| モデル | 内容 | 主な項目 |
|---|---|---|
| `User` | ログインするユーザー | ログイン ID、パスワード（ハッシュ化して保存）、登録日時 |
| `Post` | つぶやき。100 文字程度の短文 | 投稿者（`User`）、本文、投稿日時 |
| `Diary` | その日のつぶやきを AI がまとめた日記 | 書き手（`User`）、日付、本文、作成日時 |

- **ログイン認証あり**。つぶやきと日記は本人だけが見られる
- **つぶやきの本文**は必須、最大 100 文字。`PostForm` の Bean Validation と DB の列定義の両方で制限する
- **日記は 1 ユーザー 1 日 1 本**。DB に `(user_id, 日付)` の一意制約を付けて保証する
- 日記は、そのユーザーがその日に投稿したつぶやきから作る。「その日」は日本時間（Asia/Tokyo）で区切る

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
    │       ├── templates/        # Thymeleaf。機能ごとにサブディレクトリを切る（post/, diary/ …）
    │       └── static/           # 独自の CSS・JS・画像（Bootstrap は WebJars から読むので置かない）
    └── test/java/com/example/tsumory/   # main と同じパッケージ構成
```

### パッケージの分け方

- **層ごとに分ける（package by layer）**。`post/`・`diary/` のような機能ごとのパッケージは作らない
- クラスは役割に応じて次のパッケージに置き、名前の付け方をそろえる（つぶやき `Post` の例）

  | パッケージ | 役割 | クラス名 |
  |---|---|---|
  | `controller` | 画面のリクエストを受ける | `PostController` |
  | `service` | 業務ロジック・トランザクション | `PostService` |
  | `service` | AI 連携（Claude API の呼び出し） | `DiaryAiService` |
  | `service` | 例外 | `PostNotFoundException` |
  | `repository` | DB アクセス（Spring Data JPA） | `PostRepository` |
  | `domain` | ドメインモデル（エンティティなど） | `Post` |
  | `form` | Thymeleaf のフォーム入力（Bean Validation 付き） | `PostForm` |
  | `config` | 設定 | `SecurityConfig` |

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
- 追加する依存のうち、Spring Boot が管理していないもの（Anthropic SDK、Bootstrap など）はバージョンを明記する

### Java 21 の機能を積極的に使う

- **record**: 不変の値の受け渡し（service 間の結果、AI の生成結果など）は record で書く。JPA エンティティと `form/` のクラスは record にしない（JPA と Thymeleaf のフォームバインドが getter/setter を前提にするため）
- **switch 式とパターンマッチング**: 分岐は `switch` 式で書き、`instanceof` は型パターン（`if (x instanceof Post p)`）を使う。record パターン（`case Result(var a, var b) ->`）も使ってよい
- **sealed interface**: 取りうる結果が決まっているもの（例: 日記生成の「成功」「つぶやきなし」「AI が拒否」）は sealed interface + record で表し、`switch` で漏れなく扱う。`default` を書かずにコンパイラに網羅性を確認させる
- **テキストブロック**: Claude へのプロンプトや複数行の SQL は `"""` で書く
- **`var`**: 右辺から型が明らかなローカル変数に使う。型が読み取れない場合は書く
- **Sequenced Collections**: 先頭・末尾は `getFirst()` / `getLast()` / `reversed()` を使い、`get(0)` や `get(size() - 1)` は書かない
- **仮想スレッド**: 有効にしてあるので、ブロッキング I/O（DB、Claude API）は普通に同期で書く。並行処理は `Executors.newVirtualThreadPerTaskExecutor()` を使う
- **日時**: `java.time` を使う。日記の日付は `LocalDate`、日時は `Instant` か `OffsetDateTime`。「今日」は `ZoneId.of("Asia/Tokyo")` を明示して求め、テストできるように `Clock` を注入する
- **Optional**: 見つからない可能性がある 1 件の戻り値は `Optional` にする。フィールドや引数には使わない

### Spring・Lombok

- DI はコンストラクタインジェクション（`@RequiredArgsConstructor` + `private final` フィールド）
- `@Transactional` は service に付ける。読み取りだけのメソッドは `@Transactional(readOnly = true)`
- エンティティには `@Getter` と必要な `@Setter` だけを付ける。`@Data`・`@EqualsAndHashCode`・`@ToString` は付けない（遅延ロードや循環参照で問題が起きるため）
- ログは SLF4J（Lombok の `@Slf4j`）で出す

## 禁止事項

### 実装

- `@Autowired` によるフィールドインジェクション
- `System.out.println` / `printStackTrace()` によるログ出力
- 例外を握りつぶす `catch`（何もしない、ログだけ出して処理を続ける）。扱えない例外はそのまま投げる
- `null` のコレクションを返すこと（空のコレクションを返す）
- 仮想スレッド上でブロッキング I/O を `synchronized` で囲むこと（Java 21 ではキャリアスレッドを占有する）。排他が必要なら `ReentrantLock` を使う
- 自前のスレッドプール（`Executors.newFixedThreadPool` など）や `new Thread()`
- 適用済みの Flyway マイグレーションファイルの書き換え。変更は新しいマイグレーションで行う
- `spring.jpa.hibernate.ddl-auto` を `update` / `create` にすること

### セキュリティ

- **XSS**: ユーザーの入力や AI の生成結果を `th:utext` で出力しない。常に `th:text`（エスケープあり）を使う
- **SQL インジェクション**: SQL や JPQL を文字列連結で組み立てない。Spring Data のメソッド名クエリか、`@Query` + バインドパラメータを使う
- **CSRF**: CSRF 対策を無効にしない（`csrf.disable()` 禁止）。フォームは `th:action` を使い、トークンを自動で埋め込ませる
- **パスワード**: 平文やハッシュ化なしで保存しない。`PasswordEncoder`（`PasswordEncoderFactories.createDelegatingPasswordEncoder()`）でハッシュ化する
- **他人のデータ（IDOR）**: つぶやきや日記を ID だけで取得しない。必ずログイン中のユーザーを条件に含める（例: `findByIdAndUser(id, user)`）。他人のデータにアクセスされた場合は 404 を返す
- **認可**: `permitAll()` はログイン画面・ユーザー登録・静的リソース（`/webjars/**` など）だけ。それ以外は認証必須にする
- **秘密情報**: API キーやパスワードをコード・設定ファイル・コミットに含めない。`.env` などはコミットしない
- **ログ**: パスワード、API キー、セッション ID をログに出さない。つぶやきと日記の本文も個人的な内容なのでログに出さない
- **エラー表示**: スタックトレースや SQL などの内部情報を画面に出さない
- **入力の受け取り**: リクエストを直接エンティティにバインドしない。必ず `form/` のクラスで受け、Bean Validation を通す（`@Valid`）
- **プロンプトインジェクション**: つぶやきの本文をシステムプロンプトに埋め込まない。ユーザーメッセージ側にデータとして渡し、本文の中の指示に従わないようシステムプロンプトで指示する。AI の出力は表示用の文章としてだけ扱い、その内容で処理を分岐させない
- **リダイレクト**: リクエストパラメータの URL にそのままリダイレクトしない（オープンリダイレクト）

## AI（Claude API）

- Anthropic Java SDK を使い、HTTP を直接呼ばない
- モデルは `claude-opus-5-5`
- API キーは環境変数 `ANTHROPIC_API_KEY` から読む。コードや設定ファイルに書かない

## 注意点

- Spring Security が既定で全 URL に認証をかける。`/webjars/**` や静的リソースは、ログイン前でも読めるように許可が必要
- Thymeleaf から Bootstrap を読むときはバージョンなしのパスを使う（`webjars-locator-lite` が解決する）
  - `@{/webjars/bootstrap/css/bootstrap.min.css}`
  - `@{/webjars/bootstrap/js/bootstrap.bundle.min.js}`

## Git

- ブランチは `main`。リモートは https://github.com/fuji-t88/tsumory
- コミットメッセージは英語。変更の種類ごとにコミットを分ける
