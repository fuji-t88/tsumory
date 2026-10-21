# tsumory

ユーザーが投稿した「つぶやき」を、AI（Claude）が日記にまとめるWebサービス。

## 仕様書

仕様は `docs/` にある。入口は [docs/spec.md](docs/spec.md)（各仕様書の役割を示す索引）。

- **何を作るか**（コンセプト、機能、画面、データ、処理、AI 呼び出しの設定）は仕様書に書き、仕様書を正とする。このファイルには書かない
- **どう作るか**（技術スタック、開発環境、構成、規約、禁止事項）はこのファイルに書く。仕様書には書かず、必要なら「CLAUDE.md の〜に従う」と参照する
- 仕様書とこのファイルが食い違っていたら、実装を始める前にどちらが正しいかを確かめる

## 原則の扱い

- このファイルの原則は、絶対のルールではなく、文脈で評価する道具として使う。原則どうしがぶつかることもある（例：「不変条件はエンティティに閉じる」と「エンティティは `Clock` を持たない」）。目の前の局面で本当に妥当かを問い直して決める
- AI が返した評価（コードレビューの指摘など）も絶対視しない。プロジェクトの方針（多層防御を取る、将来の拡張に備えるなど）に照らして、追加で何を守るか、何を指摘にしないかを判断する
- 原則から外れると判断したとき、または指摘にしないと判断したときは、その理由をレビュー文書（`docs/code-review-*.md`）や仕様書に残す。チャットは流れて消えるので、後で「なぜそうしたか」を振り返れるようにする
- ただし「禁止事項」のセキュリティの項目（秘密情報、パスワード、XSS、SQL インジェクション、CSRF、IDOR など）は、文脈によらず守る

## テーブル概要

全体像だけを示す。列の定義・制約・ER 図は [docs/phase1-spec.md](docs/phase1-spec.md) の「2. データベース」、エンティティの振る舞いは「3. ドメインモデル」を正とする。

| テーブル | エンティティ | 概要 |
|---|---|---|
| `users` | `User` | ログインするユーザー |
| `posts` | `Post` | つぶやき。ユーザーごとに 1 日 16 件まで |
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

- `./gradlew bootRun` で起動すると、Spring Boot の Docker Compose 連携で `compose.yaml` のコンテナ（`db`・`adminer`）も自動で起動する（Docker Desktop の起動が必要）。`app` は起動しない（「compose の構成」）
  - `db`: PostgreSQL。`localhost:5432`（このマシンからだけつなげる）、DB 名・ユーザー名 `tsumory`、パスワード `secret`（`.env` で上書きしなければこの初期値）
  - `adminer`: DB 管理画面。http://localhost:8081 （サーバー欄は `db`）
- アプリは http://localhost:8080
- DB の接続情報は Docker Compose 連携が自動で設定するので、`application.yaml` には書かない
- データはボリューム `postgres-data` に永続化している。ボリューム名を変えるとデータが消えるので変えない
- 動作確認用のテストデータは `db/dev-seed.sql`。初期ユーザー `demo` に、過去 7 日分のつぶやきと日記（2 日は日記なし）を入れる。`docker compose exec -T db psql -U tsumory -d tsumory -v ON_ERROR_STOP=1 < db/dev-seed.sql` で流すか、Adminer の「SQL コマンド」に貼り付けて実行する
  - 流すたびに、`demo` の「7 日前〜昨日」のつぶやきと日記を消してから入れ直す（自分で投稿した分も消える）。詳しくはファイル冒頭のコメント
  - 本番に入らないよう、`src/main/resources/db/migration` には置かない。テストデータを Flyway のマイグレーションとして足さない
- Claude API のキーは、プロジェクト直下の `.env` に `ANTHROPIC_API_KEY=sk-ant-...` と書く（`.gitignore` 済み。コミットしない）
  - `application.yaml` の `spring.config.import: optional:file:.env[.properties]` で、Spring Boot の標準機能として読む。`.env` を読むためだけのライブラリ（dotenv 系など）は追加しない
  - プロパティファイルとして読むので、`export` や値を囲む引用符は書かない（引用符も値に含まれてしまう）
  - OS の環境変数が同じ名前で設定されていれば、そちらが優先される。本番は `.env` を置かず、OS の環境変数で渡す

## コマンド

- `./gradlew bootRun` — 起動
- `./gradlew build` — ビルドとテスト
- `./gradlew test` — テストのみ
- `./gradlew spotlessApply` — 整形
- `docker build -t tsumory .` — 本番用のイメージを作る（「本番のコンテナ」）
- `docker compose --profile deploy up --build` — アプリもコンテナで動かす（本番相当の構成を手元で確かめる。「compose の構成」）
- 本番へのデプロイは手で行わない。`main` に push すると、GitHub Actions がテストを流し、通れば Railway にデプロイする（「CI/CD」）

## 本番のコンテナ

- 本番は Railway。Railway は `Dockerfile` だけを使ってアプリをビルドし、PostgreSQL は Railway のマネージドサービスを使う。`compose.yaml` は本番では使わない（Adminer も本番には入らない）
- イメージはプロジェクト直下の `Dockerfile` で作る。ビルドの中でテストは流さないので、先に `./gradlew build` を通す
- イメージを小さくするため、Java のランタイムは JDK 全体ではなく、jlink でアプリが使うモジュールだけを入れて Alpine の上で動かす
  - 依存を足して起動時に `ClassNotFoundException`（`java.*`・`jdk.*` のクラス）が出たら、`Dockerfile` の `--add-modules` にそのモジュールを足す。要るモジュールは、展開した jar に `jdeps --print-module-deps` を当てて調べる
  - `jdk.crypto.ec`（Claude API との TLS）と `jdk.localedata`（日本語の曜日）は jdeps では見つからないので、消さない
- `Dockerfile` に `RUN --mount=type=cache` を書かない。Railway はキャッシュの mount に独自の形の ID（`id=s/<サービス ID>-…`）を求め、普通の書き方では Dockerfile の検証で止まる（2026-10-19 に Railway のビルドが「The Dockerfile failed validation」で失敗した）。依存はソースより先に取ってくるので、レイヤーキャッシュで足りる
- `.dockerignore` は、ビルドに要るファイルだけを通す書き方にする（`.env` などが気づかないうちにイメージに入らないように）。ビルドに要るファイルを足したら、ここにも足す
- 実行時に環境変数で渡すもの（イメージには入れない）
  - `ANTHROPIC_API_KEY`
  - `SPRING_DATASOURCE_URL`・`SPRING_DATASOURCE_USERNAME`・`SPRING_DATASOURCE_PASSWORD`（開発では Docker Compose 連携が自動で設定する値。`spring-boot-docker-compose` は jar に入らない）
  - `APP_GATEWAY_USERNAME`・`APP_GATEWAY_PASSWORD`（設定 `app.gateway.username`・`app.gateway.password`。公開 URL の Basic 認証。両方を渡したときだけ有効、片方だけ・空白だけなら起動しない。phase1-spec.md の「7. セキュリティ > 公開 URL の Basic 認証」）
    - 開発の `.env` には書かない。`.env` はテストでも読まれるので、書くと controller のテストが 401 で落ちる
    - 有効にすると、ログイン画面も 401 になる。Railway の「Healthcheck Path」（`/login`）はデプロイの失敗になるので、Basic 認証を有効にするなら外す
- SQL とバインドパラメータのログ（`application.yaml` の `org.hibernate.SQL`・`org.hibernate.orm.jdbc.bind`）は、イメージの環境変数で `INFO` にして出さない（禁止事項の「ログ」）。この 2 つの環境変数を外さない
- 「今」の基準は、イメージの環境変数 `APP_CLOCK_SOURCE=database` で DB の時刻にする（複数のインスタンスで時計をそろえるため。phase1-spec.md の「5. 処理の詳細 > 現在時刻の基準（本番は DB の時刻）」）。この環境変数を外さない。`deploy` プロファイルの `app` も同じイメージなので DB の時刻になる
- コンテナは非 root のユーザー `app` で動かす。タイムゾーンは `TZ=Asia/Tokyo`（ログの時刻を日本時間にする。「今日」は `Clock` が決めるので、これには左右されない）

### compose の構成

- `compose.yaml` は 1 つだけ。ローカルで使う構成で、本番（Railway）では使わない
  - 既定は開発用（`db`・`adminer`）。`deploy` プロファイルを付けると、`app`（`Dockerfile` からビルドしたアプリ）も起動する本番相当の構成になる
  - `app` には `profiles: ['deploy']` を付ける。外さない。`bootRun`（Spring Boot の Docker Compose 連携）は profiles のないサービスだけを起動するので、付けないと `bootRun` のアプリとは別に `app` のコンテナまで起動し、ビルドが走ってポート 8080 を取り合う
- DB の接続情報と API キーは `${VAR:-初期値}` で書き、`.env` で上書きできるようにする。使う変数の一覧は `.env.example`（値は書かずにコミットする）。変数を足したら `.env.example` にも足す
  - DB 名・ユーザー名・パスワードの初期値は `tsumory`・`tsumory`・`secret`。`.env` に書かなくても開発で動く。アプリの接続先（`SPRING_DATASOURCE_*`）も同じ変数から組み立て、DB とずれないようにする
  - `ANTHROPIC_API_KEY` は初期値を持たせない（`${ANTHROPIC_API_KEY}`）。シークレットに初期値を書かない
  - `.env` は、`docker compose`（値の差し込み）と開発時の Spring Boot（`spring.config.import`）の両方が読む。シェルの環境変数は `.env` より優先される
  - `POSTGRES_DB`・`POSTGRES_USER`・`POSTGRES_PASSWORD` は、DB のボリュームが空のとき（初回）だけ使われる。あとから値を変えても DB は変わらず、アプリだけが新しい値でつなごうとして失敗する
- `db`・`adminer`・`app` のポートは `127.0.0.1` にだけ公開する（初期値のパスワードの DB を、同じネットワークのほかの端末に見せない）
- `deploy` プロファイルのアプリも、開発と同じボリューム（`postgres-data`）を使う。止めるときに `docker compose down -v` を使うと開発のデータが消える
- `app` のヘルスチェックはログイン画面（`/login`）を見る（Actuator は入れていない）
- 経緯（2026-10-19）：はじめは、本番でも compose を使う前提で、開発用の `compose.yaml` と本番用の `compose.prod.yaml` に分けていた（本番ではパスワードと API キーを必須にし、初期値の `secret` で動かないようにするため）。本番は Railway で `Dockerfile` だけを使い、compose はローカルでしか使わないと分かり、前提が変わったので 1 つのファイルにまとめた。本番で compose を使うことになったら、パスワードの初期値の扱いを見直す

### CI/CD

- GitHub Actions の `.github/workflows/ci.yml`。`main` への push と pull request で、`./gradlew spotlessCheck`（整形の確認）、`./gradlew test` の順に別のステップで流し、`main` への push で両方が通ったときだけ Railway にデプロイする
  - ステップを分けるのは、整形とテストのどちらで落ちたかを Actions の画面で見分けられるようにし、整形の確認が先に走ることをワークフローに書いておくため。jar は Railway が `Dockerfile` でビルドするので、CI では作らない（`build` は流さない）
  - ワークフローの `env` に `CI: true` を書き、整形は直さず `spotlessCheck` で落とす（「コーディング規約 > 全般」）。GitHub Actions も `CI=true` を設定するが、それに頼らず明示する。`CI` がないと `compileJava` の前に `spotlessApply` が走り、崩れた整形を黙って直してから確かめるので、関門にならない。この `env` を消さない。push する前に手元で `./gradlew build` を通す
    - 2026-10-20 に、CI と同じ条件のクローンでわざと整形を崩して `CI=true ./gradlew build` を流し、`spotlessJavaCheck` で失敗し、ファイルが直されずに残ることを確かめた
  - 失敗したときは、テストの結果（`build/reports/tests/test/`）を Actions の Artifacts に 7 日残す
- テストは DB にも Claude API にもつながないので、CI には PostgreSQL も API キーも `.env` も置かない（2026-10-20 に、`.env` のないクローンで `CI=true ./gradlew build` が 394 件とも通ることを確かめた）。テストに DB（Testcontainers）や秘密情報が要るようになったら、ワークフローも直す
- デプロイは Railway CLI の `railway up --ci`。ソースを Railway に送り、Railway が `Dockerfile` でビルドする（「本番のコンテナ」）。ビルドが失敗すれば、ジョブも失敗する
  - CLI はバージョンを固定して `npx` で動かす。上げるときは、出てから 2 週間以上たったものにする
  - トークンは GitHub の Environment `production` の Secret `RAILWAY_TOKEN`（Railway のプロジェクトトークン。production 環境に限る）。ワークフローやコードに書かない
  - Railway のサービスは GitHub のリポジトリとつながない（Railway の自動デプロイを止める）。つないだままだと、テストの結果を待たずに Railway もデプロイし、1 回の push で 2 回デプロイされる
  - デプロイは並べて順に流し、途中で止めない（`concurrency` の `cancel-in-progress: false`）
  - ジョブが確かめるのはビルドまでで、アプリが起動したかは見ない（Basic 認証を有効にしたので、Railway のヘルスチェックを外している）。デプロイの後は公開 URL を開いて確かめる

## ディレクトリ構成

```
tsumory/
├── compose.yaml                  # ローカル用コンテナ（db, adminer。deploy プロファイルで app も）。「compose の構成」
├── .env.example                  # .env のひな形（値は書かない）
├── Dockerfile                    # 本番用のイメージ（「本番のコンテナ」）
├── .github/workflows/ci.yml      # テストと Railway へのデプロイ（「CI/CD」）
├── .dockerignore
├── db/
│   └── dev-seed.sql              # 動作確認用のテストデータ（手で流す。Flyway のマイグレーションではない）
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
  | `service` | カテゴリ分類の AI 連携（Claude API の呼び出し。インターフェースに分けない。フェーズ 2） | `PostCategoryClassifier` |
  | `service` | 例外 | `ResourceNotFoundException` |
  | `repository` | DB アクセス（Spring Data JPA） | `PostRepository` |
  | `domain` | ドメインモデル（エンティティなど） | `Post` |
  | `form` | Thymeleaf のフォーム入力（Bean Validation 付き） | `PostForm`、`PostEditForm`、`SearchForm`、`DiaryListForm` |
  | `config` | 設定 | `SecurityConfig` |
  | `config` | `application.yaml` の設定値を受ける record（`@ConfigurationProperties` + `@Validated`） | `ClaudeProperties` |
  | `config` | リクエストごとにリクエスト ID とユーザー ID をログに付け、リクエストの結果を記録する（`OncePerRequestFilter`） | `RequestLoggingFilter` |
  | `config` | 本番の公開 URL を、アプリのログインより手前で Basic 認証で守る（`OncePerRequestFilter`。環境変数があるときだけ有効） | `GatewayAuthFilter` |
  | `config` | 本番の「今」を DB の時刻に合わせる時計（`Clock` の実装。`ClockConfig` が設定で切り替える） | `DatabaseClock` |
  | `config` | ログイン中のユーザー（`UserDetails` を実装する record。`@AuthenticationPrincipal` で受け、`userId()` を service に渡す） | `CustomUserDetails` |
  | `config` | ログイン時に DB からユーザーを探し、`CustomUserDetails` に詰めて返す（`UserDetailsService` の実装） | `CustomUserDetailsService` |

- パッケージは上の 6 つだけにする。`ai/`・`client/`・`exception/`・`entity/`・`dto/`・`common/`・`util/` などは作らない
- AI 連携と例外は `service/` にまとめる
- 依存の向きは controller → service → repository。controller から repository を直接呼ばない
- ドメインモデルを画面にそのまま渡さず、入力は `form/` のクラスで受ける
- Claude API（Anthropic SDK）は `service/` の AI 連携クラスからだけ使う

## 機能を足す手順

機能を足すときは、**データに近い層から外側へ**、次の順に作る（フェーズ 1・フェーズ 2 と同じ順序）。

1. データベース —— Flyway のマイグレーションでスキーマを変える（「コーディング規約 > 全般」のマイグレーションの方針に従う）
2. ドメイン層 —— エンティティにフィールドを足す、列挙型を定義する（`domain/`）
3. リポジトリ層 —— 新しいクエリメソッドを足す（`repository/`）
4. サービス層 —— ビジネスロジックを足す・直す（`service/`）
5. コントローラー層 —— エンドポイントを足す（`controller/`・`form/`）
6. テンプレート —— 画面を新しく作る、または直す（`templates/`）

- 理由：下の層から積み上げれば、各ステップで依存先がすでにあり、確定した土台の上にコードを組み立てられる。順序を崩すと、まだない下の層の形を推測で先回りして作ることになり、土台が固まらないまま積み上がって手戻りが増える（例：画面より先にエンドポイントを作り、画面ができてから戻り先を決め直す）
- 外側の層（画面や URL）から依頼されたときも、足りない下の層があれば、そちらから作る
- 各層を作り終えるたびに `./gradlew build` が通る状態にしてから、次の層へ進む
- 仕様書の「実装の順番」（どの機能から作るか）は機能の順番。各機能の中は、この層の順で作る

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
- **日時**: `java.time` を使う。日記の日付は `LocalDate`、日時は `Instant` か `OffsetDateTime`。「今日」は `ZoneId.of("Asia/Tokyo")` を明示して求める（時刻の取り方は下の「時刻の取得」）
- **Optional**: 見つからない可能性がある 1 件の戻り値は `Optional` にする。フィールドや引数には使わない

### 時刻の取得

- 現在時刻は、注入した `Clock`（`config/ClockConfig` の Bean。`Asia/Tokyo`）から取得する。`Instant.now()`・`LocalDate.now()`・`LocalDateTime.now()` などを引数なしで直接呼ばない（`clock.instant()`、`LocalDate.now(clock)` と書く）
- エンティティのように Bean でないオブジェクトには `Clock` を注入しない。サービス層で求めた `Instant` を引数で渡す（例: `new Post(user, content, clock.instant())`、`post.edit(content, clock.instant())`）
- 理由：テストで `Clock.fixed(...)` を渡せば時刻を固定でき、結果が毎回同じになる。タイムゾーンの基準も `Clock` の 1 か所にまとまり、本番用の `Clock` に差し替えるときもコードを変えずに済む
- 本番は、同じ `Clock` の Bean を DB の時刻に合わせた時計（`config/DatabaseClock`）に差し替えている（設定 `app.clock.source`。phase1-spec.md の「5. 処理の詳細 > 現在時刻の基準（本番は DB の時刻）」）。service・controller は差し替えを知らないので、時刻を使うコードは今までどおり `Clock` から取る。DB に直接 `now()` を問い合わせて「今」を決める処理を、service や repository に書かない（基準が 2 つになる）
- エンティティの決まりのために `Clock`（や「今日」）を引数で渡したくなったら、まずその決まりが業務ルールでないかを考え、service に置けないかを検討する（「入力の検証（多層防御）」の制約の置き場所）。業務ルールなら service で守れば、この原則とも衝突しない

### Spring・Lombok

- DI はコンストラクタインジェクション（`@RequiredArgsConstructor` + `private final` フィールド）
- `@Transactional` は service に付ける。クラスに `@Transactional(readOnly = true)` を付けて既定を読み取り専用にし、状態を変えるメソッドだけに `@Transactional` を付けて上書きする（例: `PostService`）。外部 API を呼ぶメソッドは `@Transactional(propagation = Propagation.NOT_SUPPORTED)` にし、API の応答を待つ間はトランザクションを張らない（例: `DiaryGenerationService#generateForDate`）
- エンティティには `@Getter` と必要な `@Setter` だけを付ける。`@Data`・`@EqualsAndHashCode`・`@ToString` は付けない（遅延ロードや循環参照で問題が起きるため）
- エンティティの引数なしコンストラクタは `@NoArgsConstructor(access = AccessLevel.PROTECTED)` にする（JPA 用。アプリのコードからは、必須の値を受け取るコンストラクタで作る）
- エンティティの関連（`@ManyToOne` など）は `fetch = FetchType.LAZY` を明示する（`@ManyToOne` の既定は EAGER で、一覧を取得すると関連先ごとに SELECT が発行される N+1 問題の原因になるため）
- `NOT_SUPPORTED` のメソッドの中で DB を読むときも、短いトランザクション（`TransactionTemplate`）で囲む（例: `PostService#create` の上限の確認）。同じクラスのメソッドを直接呼ぶとトランザクションを持たず、Spring がその範囲に結び付けた `EntityManager` が、DB 接続をメソッドの終わりまで（外部 API の応答を待つ間も）握り続けるため
- `spring.jpa.open-in-view` は `false` のままにする（有効だと、リクエストの終わりまで DB 接続を握る）。そのため controller・テンプレートでは遅延ロードの関連を読まない（`LazyInitializationException` になる）。表示用の値は service で record にして渡す

### 入力の検証（多層防御）

ユーザーの入力の決まりは、1 か所だけで守らない。フォームを通らない経路（将来の API・管理用の処理・テストデータ）や、改修でチェックが外れたときにも守りが抜けないようにする。

- 決まりは次の層で重ねて守る

  | 層 | 守り方 | 例 |
  |---|---|---|
  | フォーム | Bean Validation（`@NotBlank`・`@Size`・`@Pattern`）。入力値を残して再表示する | `PostForm`・`PostEditForm` |
  | ドメイン | エンティティのコンストラクタと状態を変えるメソッドで確かめ、破っていれば例外を投げる | `Post`・`Diary`・`User` |
  | DB | 書けるものは検査制約（`CHECK`）。DB に直接書く経路（`db/dev-seed.sql`・Adminer・手で流す SQL）に備える | `posts_content_no_invisible_chars` |

- 制約は、仕様が変わったときに動くかどうかで置き場所を分ける。「不変条件をエンティティに閉じる」は、すべての制約に当てはまるわけではない

  | 種類 | 見分け方 | 置き場所 | 例 |
  |---|---|---|---|
  | 内部一貫性 | 仕様が変わっても変わらない（データとして矛盾しないための制約） | エンティティ。生成経路を問わず守る | つぶやきの本文の形（空でない・改行や見えない文字がない）、日記の生成時刻はその日の始まり以降、パスワードは BCrypt のハッシュ |
  | 業務ルール | 仕様が変われば動きうる（ユーザー操作の意味的な制約） | service（業務ロジックの集約点） | 未来の日付の日記は書けない、登録日より前の日記は開けない、1 日 16 件までしか投稿できない |

  - 業務ルールまでエンティティに閉じると、仕様変更（予約投稿、過去データの取り込みなど）のたびにエンティティの署名や例外が動き、変化に追いつけなくなる
  - 業務ルールは「今日」（時計）や登録日（DB）に依存することが多い。エンティティは `Clock` を持たない（「時刻の取得」）ので、service に置くとこの原則とも衝突しない
- エンティティは、内部一貫性の制約を自分で守る
  - 必須の値の `null` は `Objects.requireNonNull(値, "名前")`（`NullPointerException`）、決まり違反は `IllegalArgumentException` にする
  - 例外のメッセージに値を入れない（本文やパスワードがログに出ないように）
  - 状態を変えるメソッドは、すべての値を確かめてから書き換える（途中で失敗して、一部だけ書き換わった状態にしない）
  - タイムゾーンが要る制約は、ゾーンを持たずに判定できる形を探す（例：`Diary` は、世界でいちばん早くその日になる `ZoneOffset.MAX` で比べる。正しいデータを誤って弾かないかわりに、少しの矛盾は見逃す。見逃す範囲はテストで明示する）
- 上限や形式は定数 1 つから各層に配る。数字や正規表現を層ごとに書かない
  - Java：エンティティや form の定数を `@Size(max = ...)`・`@Column(length = ...)`・`@Pattern(regexp = ...)` で使う（例: `Post.CONTENT_MAX_LENGTH`・`Post.NO_INVISIBLE_CHARS_REGEX`）。文言も `{max} 文字以内で入力してください` のように数字を書かない
  - テンプレート：`maxlength` は、`InputLimitsAdvice`（`@ControllerAdvice`）が渡す値を `th:maxlength` で使う。Thymeleaf 3.1 は式の中での `T(...)` による static の参照を制限するので、定数はモデルで渡す
  - DB：PostgreSQL の正規表現は Unicode の文字の種類（`\p{Cf}` など）を使えない。Java の定義から文字の範囲を書き出して SQL に書き、食い違いがないことをテストで確かめる（`PostContentCheckMigrationTest`）。範囲は `\uXXXX` の形で書き、見えない文字をファイルに直接書かない
- 既存のテーブルに検査制約を足すときは `NOT VALID` を検討する。ただし `NOT VALID` でも、既存の行を UPDATE するとその行は確かめられる。残すと決めた古いデータ（フェーズ 1 の改行入りのつぶやきなど）が破る条件は、制約に入れない
- AI のプロンプトに渡るユーザーの文字列は、見えない文字（改行以外の制御文字・書式文字・行と段落の区切り。`Post.INVISIBLE_CHAR_CLASS`）を入口で拒否する。画面では無害に見える文に指示を隠せるため。プロンプトを組み立てるときにも同じ文字を取り除く（入口の守りより前に保存されたデータに備える）
  - 見える文字で書いた指示を、言葉（「無視して」など）で弾くことはしない。言い換えで抜けられ、普通の文まで弾いてしまうため。禁止事項の「プロンプトインジェクション」の守りで扱う

### テンプレート・画面

- テンプレートのコメントは Thymeleaf のコメント（`<!--/* */-->`）で書く。HTML のコメント（`<!-- -->`）はそのままブラウザに送られる
- インラインのイベントハンドラ（`onclick`・`onchange`・`onsubmit` など）やインラインの `<script>` は書かない。スクリプトは `static/js/` に置き、`addEventListener` で付ける（Content-Security-Policy で `unsafe-inline` を禁止できるようにするため）。フォームの二重送信の防止は `form-submit.js` の `js-submit-once` を使う
- エラー画面は `templates/error/404.html`・`500.html` と、ほかの状態コードの受け皿の `templates/error.html`。どれかがないと Spring Boot の英語の画面（Whitelabel Error Page）になる。エラー応答の設定は `spring.web.error.*` に書く（Spring Boot 4 では `server.error.*` は非推奨）

### コメントとドキュメントの置き場所

「なぜこの設計にしたのか」（設計の意図）は仕様書に書き、コードのコメントには書かない。仕様書を直せば AI の理解も変わるが、コードのコメントは直し忘れやすく、実装とずれていく（このプロジェクトでも、仕様書が追いついたあとに「原稿にはないが」と書いたまま残ったコメントがあった。[docs/refactor-comments.md](docs/refactor-comments.md)）。

| 書くもの | 置き場所 |
|---|---|
| 設計の意図（なぜこの決まり・構成・判断にしたか） | 仕様書（`docs/`。入口は [docs/spec.md](docs/spec.md)） |
| 作り方の決まりとその理由（技術スタック・規約・禁止事項） | このファイル |
| 振る舞い（何をするか、何を拒否するか） | テスト。テスト名で振る舞いを書く（テストは毎回流れるので、実装と食い違えば落ちる） |
| 公開するメソッドの契約（引数・戻り値・投げる例外・呼び出し側が守る前提） | JavaDoc |
| その行を読む人が、その場で要る事情（処理の順番の理由、こう書かないと壊れる理由） | 行コメント |

- 理由に触れるときは、仕様書かこのファイルの節を参照する。書き方は「（phase2-spec.md の「6. 処理の詳細 > 日付の範囲」）」「（CLAUDE.md の「Spring・Lombok」）」。「仕様 6 章」のように、どのファイルか分からない書き方はしない
- 仕様書やこのファイルの節の名前を変えたら、`git grep` で参照しているコメントを探して直す
- JavaDoc は、service・domain の公開メソッドのうち、業務ルールを含むもの・例外を投げるもの・呼び出し側が守る前提があるものに付ける。名前と型で分かるもの（`today()`・`countPosts()` など）には付けなくてよい
- コードを読めば分かることは書かない（例：「クラスに `@RequestMapping("/diary")` があるので、メソッドのパスは相対で書く」）

### テスト

- テスト名は日本語で、振る舞いを書く（例：`決まりを破る本文には直せず元の本文のまま`）。テスト名がそのまま仕様の一覧として読めるようにする
  - service・repository のテストには英語の名前（`create_rejectsWhenDailyLimitReached_withoutCallingClaude` など）が残っている。そのテストクラスを直すときに、日本語に直す
- 守り（入力チェック・制約・例外）を足したら、本体の変更を一度外して、そのテストが失敗することを確かめる（テストが空振りしていないか）
- `@WebMvcTest`（MockMvc）はエラー画面への切り替えを再現しない。エラー画面は、テスト用の controller から描画するか、`/error` をエラーの属性を付けて呼んで確かめる。実際の動き（状態コードごとの画面の選び分けなど）は、ランダムポートでサーバーを立てる一時的なテストで確かめ、確かめたら消す
- DB の制約のように自動テストできないもの（Testcontainers を入れるまで）は、開発 DB でトランザクションの中で確かめ、最後にロールバックする。開発 DB にデータを残さない

### ログ

- ログは SLF4J（Lombok の `@Slf4j`）で出す。本番で障害の原因を追えるよう、service の状態を変える処理と外部 API の呼び出しには記録を残す
- ログのメッセージは英語で書く（`Post created: ...`・`Diary created: ...`）。言語がまざると、ログの検索・集計で漏れるため
- 出してよいのは、個人情報を含まないメタ情報だけ（ユーザー ID、つぶやき・日記の ID、日付、件数、本文の文字数、結果の種類、処理時間、Claude API のステータスコード・リクエスト ID・メッセージ ID・トークン数）。出してはいけないものは禁止事項の「ログ」
- 例外はメッセージを出さず、クラス名だけを出す（SQL や API の応答が含まれることがあるため）
  - 例外: Claude API の呼び出しが例外で失敗したとき（ネットワーク障害・レート制限・タイムアウト・API のエラー）は、原因を調べられるよう、例外を渡してスタックトレース付きで出す（`log.error("...", e)`）。SDK の例外のメッセージには API のエラー応答（種類と説明）が入る。プロンプトやつぶやきの本文をこちらから例外やログに入れることはしない
- ログレベルは次のとおり使い分ける

  | レベル | 使う場面 | 例 |
  |---|---|---|
  | ERROR | システムが正常に動いておらず、すぐ対応が必要なとき。正常な業務処理には使わない（アラートが形骸化するため） | DB に接続できない、Claude API の呼び出しが例外で失敗した（外部 API が応答しない。エラー・タイムアウト・レート制限） |
  | WARN | 業務エラー。処理は続けられたが、繰り返すなら調べるべきとき | 投稿の上限超過、つぶやき 0 件での日記生成、未来・登録日より前の日付、今日と昨日以外のつぶやきの編集、他人のつぶやきへのアクセスの試行、Claude API の応答は返ったが日記にできない（拒否・途中で止まった・本文が空） |
  | INFO | 正常な業務イベント（スタックトレースは出さない） | つぶやきの投稿・編集・削除、日記の生成・保存 |
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
- 例外を握りつぶす `catch`（何もせず、ログにも残さない）。`catch` するなら最低でも `warn` / `error` でログに残し、扱えない例外は再 throw する（「コードレビューの観点 > エラーハンドリング」）
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
  - 検索キーワードと、つぶやきのカテゴリ（AI の判定・ユーザーが直した値とも。どのカテゴリになったか）も出さない。どちらもつぶやきや日記の内容を推測できるため（フェーズ 2）
  - 例外: 開発中は、`application.yaml` の `org.hibernate.orm.jdbc.bind: TRACE` で SQL のバインドパラメータの値を出してよい。本文が含まれるので、本番デプロイ時は必ず無効化する
- **エラー表示**: スタックトレースや SQL などの内部情報を画面に出さない
- **入力の受け取り**: リクエストを直接エンティティにバインドしない。必ず `form/` のクラスで受け、Bean Validation を通す（`@Valid`）
- **プロンプトインジェクション**: つぶやきの本文をシステムプロンプトに埋め込まない。ユーザーメッセージ側にデータとして渡し、本文の中の指示に従わないようシステムプロンプトで指示する。AI の出力は表示用の文章としてだけ扱い、その内容で処理を分岐させない
  - 例外: カテゴリ分類（フェーズ 2）の出力は、決められた候補の値（`PostCategory`）と照らし合わせた場合に限り、保存する値として使ってよい。候補にない値や読めない出力はそのまま使わず、判定できなかったもの（空の `Optional`）として扱う。DB には検査制約を張らないので、この照らし合わせを省かない
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
  - `PostCategoryClassifier`（`@Component`）：つぶやきのカテゴリを分類する（フェーズ 2）
- モデル・effort・`max_tokens`・タイムアウトは `application.yaml` に書き、`ClaudeProperties` で受ける。コードに定数で書かない
  - 日記：`anthropic.*`（`model`・`effort`・`max-tokens`。`timeout` はクライアント全体で共有）
  - カテゴリ分類：`anthropic.classification.*`（`ClaudeProperties` の入れ子の record `Classification`）。投稿・編集の応答が分類を待つので、`timeout` は分類だけ短くし、リクエストごとの設定（SDK の `RequestOptions`）で渡す
- 起動前に環境変数 `ANTHROPIC_API_KEY` を設定する

## 注意点

- Spring Security が既定で全 URL に認証をかける。`/webjars/**` や静的リソースは、ログイン前でも読めるように許可が必要
- Thymeleaf から Bootstrap を読むときはバージョンなしのパスを使う（`webjars-locator-lite` が解決する）
  - `@{/webjars/bootstrap/css/bootstrap.min.css}`
  - `@{/webjars/bootstrap/js/bootstrap.bundle.min.js}`

## コードレビューの観点

実装時のセルフレビュー・プルリクエストレビューでは、コーディング規約・禁止事項に加えて以下の観点を必ず確認する。

### 可読性・保守性

- ドメイン用語（つぶやき = `Post`、日記 = `Diary`、カテゴリ = `PostCategory`）がクラス・メソッド・テンプレート・ログメッセージで一貫して使われているか。
- メソッドの責務が単一か。`service/` のクラスでリポジトリ呼び出しと Claude API 呼び出しが直接混ざらず、`PostCategoryClassifier` / `DiaryWriter` のような集約クラスを介しているか。
- Java 21 機能（record / switch 式 / テキストブロック / var）と Lombok の使い分けがコーディング規約と整合しているか。
- マジックナンバーや裸の文字列がないか。閾値は `DAILY_POST_LIMIT` のような定数、列挙は `PostCategory` のような enum で表現されているか。
- 設計の意図が仕様書に書かれ、コードのコメントが仕様書や CLAUDE.md を繰り返していないか。コメントは、その場所でしか分からない事情だけを書き、理由は節への参照にしているか（「コーディング規約 > コメントとドキュメントの置き場所」）。
- テスト名で振る舞いが読み取れるか。フィクスチャは `TestFixtures` に集約され、各テストは Arrange / Act / Assert が見分けられる構造になっているか。

### エラーハンドリング

- 書き込みを伴うサービスメソッドが `@Transactional` で個別に囲まれているか。途中で例外が起きたときに部分書き込みが残らないか。
- 業務上の失敗は専用例外（例: `ResourceNotFoundException`、`DailyPostLimitExceededException`、`InvalidDiaryDateException`）で表現され、画面では一般化されたメッセージを出すようになっているか。
- 外部依存（Claude API）の失敗時の挙動が仕様どおりか。`PostCategoryClassifier` のように分類失敗を `Optional.empty()` に潰すのか、`DiaryGenerationException` で利用側に伝播させるのかが意図的に選ばれているか。
- 例外を `catch` して握りつぶしていないか。最低でも `warn` / `error` でログに残し、必要に応じて再 throw しているか。
- ログレベルの使い分けが妥当か（業務エラー = `warn`、業務イベント = `info`、デバッグ詳細 = `debug`）。スタックトレースを `info` 以下で出していないか。
- ユーザー入力に対するバリデーション（`@Valid` / Bean Validation）が漏れていないか。長さ・必須・形式をサーバー側で検証しているか。

### セキュリティ

- 認証ユーザー以外のデータが取得・更新・削除できないか。`Post` / `Diary` の検索系は `findByIdAndUserId` のように `userId` で必ず絞り込んでいるか（IDOR 防止）。
- パスワードが `PasswordEncoder`（BCrypt）でハッシュ化されているか。平文の保存・ログ出力になっていないか。
- Thymeleaf フォームで CSRF トークンが含まれているか。`SecurityConfig` で CSRF を無効化していないか。
- SQL / JPQL がパラメータ化されているか。`@Param` を使い、文字列連結でクエリを組み立てていないか。
- ログにつぶやき・日記の本文、Claude API キー、パスワード、セッショントークンが出力されていないか。
- Claude API の生のエラー内容や例外スタックがユーザー画面にそのまま漏れていないか。
- `ANTHROPIC_API_KEY` などのシークレットが環境変数からのみ読まれているか。コード・YAML・コミット履歴に直書きされていないか。

### リファクタリングとレビューを深める 4 つの姿勢

指摘を出して直すだけで終えず、次の 4 つで一段深める。例は [docs/refactor-tests.md](docs/refactor-tests.md)。

- **数字で測る**：リファクタリングの効果は、数値で示せる状態にしておく。「読みやすくなった」で終えない
  - 前のコミットと後のコミットを同じ方法で測り、レビュー文書に表で残す。コミットと測り方も書き、同じ表をあとで作り直せるようにする
  - 測るもの：テストの件数、変異（本体の守りを 1 つずつわざと壊す）を捕まえた数、重複の数（同じ値・同じ処理を書いた場所の数）、規約違反の数など
  - 変わらなかった数字、悪くなった数字、測れなかったものも書く（効果を大きく見せない）
  - 理由：数字がないと、効果があったかを後から確かめられず、次に同じ種類のリファクタリングに手間をかけるかを決められない
- **構造で守る**：対になっているもの（作るときと直すとき、フォームとエンティティなど）の対称性は、規約やレビューの注意ではなく、仕組みで担保する
  - 例：`Post` のコンストラクタと `edit` は同じ `requireValidContent` を通す。テストは、作るときと直すときが同じケースの一覧（`PostTest#invalidContents`）を読む。「ソースに見えない文字を直接書かない」は `SourceInvisibleCharsTest` で守る
  - 仕組みにできないときは、その理由をレビュー文書に書く
  - 理由：規約は、書き手が気づかないうちに破れる。片方だけ直す改修は、差分に出ないのでレビューで見落としやすい
- **残す**：肯定評価（よくできている点）も、指摘と同じくレビュー文書に書いて蓄積する（「よくできている点」の節）。なぜ良いか（何を防いでいるか）も書く
  - 理由：何がなぜ良いのかが残っていれば、改修でそれを崩さずに済み、ほかの場所にも広げられる。指摘だけを残すと、良い設計が理由を知られないまま消える
- **更新する**：AI の判断（指摘にしない、保留にする、こう直す）は、前提と見直す条件といっしょに残す。前提が変わったら判断を見直し、古い判断は消さずに、いつ・なぜ変えたかを書き足す
  - 例：「テストのソースに見えない文字を直接書かないことの確かめは入れない（絵文字のつなぎ文字の扱いが決まっていないため）」という判断は、つなぎ文字もエスケープで書くと決めた時点で前提が変わったので見直し、`SourceInvisibleCharsTest` を入れた
  - 理由：AI の判断は、その時点のコードと前提に対して正しいだけ。前提が書かれていないと、古くなった判断が守られ続ける（「原則の扱い」）

### レビュー指摘への対処

- レビュー指摘への対処は 対処＋テスト＋ドキュメント更新＋コミット を 1 プロンプトに統合する。実装ファイルだけ変えて止めない
- リファクタリングの指摘に対処したら、前後の数字（「数字で測る」）を測ってレビュー文書に書く
- ドキュメント更新は、レビュー結果のファイル（`docs/code-review-*.md`）の該当指摘に「✓ 対応済み」と解消メモ（日付、何をどう直したか、コミット）を書き足し、まとめの表の状態も直す。直し方が仕様に関わるなら、仕様書も直す

## Git

- ブランチは `main`。リモートは https://github.com/fuji-t88/tsumory
- コミットメッセージは英語。変更の種類ごとにコミットを分ける
- リファクタリングと機能追加（振る舞いの変更）を、同じコミットに混ぜない。コミットの種類は次のとおり

  | 種類 | 本体の振る舞い | テスト |
  |---|---|---|
  | リファクタリング | 変えない | 既存のテストを変えずに、同じ結果で通る（件数も変わらない） |
  | 機能追加・不具合の修正 | 変わる | 足す・直す |
  | テストの追加 | 変えない（本体を変えない） | 足す |
  | テストの整理 | 変えない（本体を変えない） | 確かめる中身は変えず、書き方だけを変える。パラメータ化などで件数が変わるときは、理由をコミットメッセージに書く |

  - 理由：リファクタリングのコミットは、テストの結果が前と同じことで正しさを確かめられる。振る舞いの変更が混ざると、テストの結果が変わったときに、意図した変更か壊したのかを切り分けられない
  - テストの整理とテストの追加も分ける（例：ケースの一覧を共有する整理と、そのついでにケースを増やすことは、別のコミットにする）
- リファクタリングは小さなステップで進める
  1. テストがすべて通ることを確かめる（出発点の安全確認。テストの件数も控えておく）
  2. リファクタリングを 1 つだけ当てる
  3. テストを流し、すべて通り、件数も変わらないことを確かめる
  4. コミットする
  5. 次のリファクタリングへ
  - 途中で不具合や足りないテストに気づいても、そのリファクタリングのコミットでは直さない。リファクタリングをコミットしてから（または戻してから）、別のコミットで直す
