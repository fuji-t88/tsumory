# syntax=docker/dockerfile:1

# ---- ビルド：jar を作り、Spring Boot のレイヤーごとに展開する ----
FROM eclipse-temurin:21-jdk-alpine-3.20 AS builder
WORKDIR /workspace
# CI があると compileJava の前の spotlessApply が走らない（CLAUDE.md の「コーディング規約 > 全般」）
ENV CI=true

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# Git に gradlew が実行権限なしで入っている。Railway は Git のとおりに取り出すので、ここで付ける
RUN chmod +x gradlew
# ソースより先に依存を取ってくる。ソースだけを直したときは、このレイヤーのキャッシュが効く。
# RUN --mount=type=cache は使わない（Railway は独自の形の ID を求め、この書き方では Dockerfile の検証で止まる）
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src/main src/main
# テストはイメージのビルドでは流さない（./gradlew build で流す）。
# jar の名前は build.gradle.kts の version で変わるので、app.jar にそろえてから展開する（-plain.jar は起動できない jar）
RUN ./gradlew --no-daemon bootJar \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain\.jar$')" app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- Java のランタイム：アプリが使うモジュールだけを jlink で入れる ----
FROM eclipse-temurin:21-jdk-alpine-3.20 AS jre
# jdeps が見つけるモジュールに、jdeps では見つからない 2 つを足す。
# jdk.crypto.ec は Claude API との TLS（楕円曲線暗号）、jdk.localedata は日付の曜日を日本語で出すのに要る
RUN jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.scripting,java.security.jgss,java.sql,java.sql.rowset,java.transaction.xa,java.xml,jdk.jfr,jdk.unsupported,jdk.crypto.ec,jdk.localedata \
    --include-locales=en,ja \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-9 \
    --output /jre

# ---- 実行 ----
FROM alpine:3.24.2
COPY --from=jre /jre /opt/java
ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:$PATH \
    TZ=Asia/Tokyo \
    # application.yaml は開発用に SQL とバインドパラメータ（つぶやき・日記の本文を含む）をログに出す。
    # 本番では出さない（CLAUDE.md の禁止事項「ログ」）
    LOGGING_LEVEL_ORG_HIBERNATE_SQL=INFO \
    LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND=INFO \
    # 複数のインスタンスで時計をそろえるため、「今」は DB の時刻に合わせる（phase1-spec.md の「5. 処理の詳細 > 現在時刻の基準（本番は DB の時刻）」）
    APP_CLOCK_SOURCE=database

RUN addgroup -S app && adduser -S -G app -H app
WORKDIR /app
# 変わりにくい順に重ねる。アプリを直しただけなら、最後のレイヤーだけが入れ替わる
COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
