plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.3"
    id("org.owasp.dependencycheck") version "13.0.0"
    id("org.sonarqube") version "7.5.0.8588"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot 4.1.1 が決める版には脆弱性がある。Spring Boot が追いついたら消す（CLAUDE.md の「CI/CD > Snyk」）
extra["tomcat.version"] = "11.0.26"
extra["jackson-bom.version"] = "3.1.7"
extra["jackson-2-bom.version"] = "2.21.7"
extra["postgresql.version"] = "42.7.14"

// Hibernate が XML のマッピングを読むための JAXB の実装。XXE の脆弱性があり、直った版がないので外す（CLAUDE.md の「CI/CD > Snyk」）
configurations.all {
    exclude(group = "org.glassfish.jaxb")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("com.anthropic:anthropic-java:2.69.0")
    implementation("org.webjars:bootstrap:5.3.8")
    implementation("org.webjars:webjars-locator-lite:1.1.5")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // SourceInvisibleCharsTest・DocReferenceTest は docs と CLAUDE.md を読む。入力に入れないと、文書だけを直したときに
    // テストが「最新」として飛ばされる
    inputs.dir("docs").withPropertyName("docs").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("CLAUDE.md").withPropertyName("claudeMd").withPathSensitivity(PathSensitivity.RELATIVE)
}

spotless {
    java {
        palantirJavaFormat("2.102.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint("1.8.0")
    }
}

// ローカルではコンパイル前に自動整形する。CI では整形せず spotlessCheck で違反を検出させる。
if (System.getenv("CI") == null) {
    tasks.compileJava {
        dependsOn(tasks.named("spotlessApply"))
    }
}

dependencyCheck {
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
    }
}

jacoco {
    toolVersion = "0.8.15"
}

// SonarQube Cloud にカバレッジを渡すため、XML でも出す（CLAUDE.md の「CI/CD > SonarQube Cloud」）
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
    }
}

// 行のカバレッジが 85% を下回ったら落とす（CLAUDE.md の「テスト」）
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        // 既定では Java とビルドの設定しか解析しない。テンプレート・SQL・JS・CSS も解析する（.gitignore のファイルは入らない）
        property("sonar.gradle.scanAll", true)
        // .gitignore にもあるが、API キーを書くファイルなので重ねて外す
        property("sonar.exclusions", "**/.env")
        // 直さないと決めた指摘を、ルールとファイルの組で外す。理由と見直す条件は
        // docs/code-review-static-analysis.md の「SonarQube Cloud（2026-10-22）」の「指摘にしなかったもの」
        val ignored =
            mapOf(
                "fragmentTitle" to ("Web:PageWithoutTitleCheck" to "src/main/resources/templates/fragments/**"),
                "loginAutofocus" to ("Web:S9379" to "src/main/resources/templates/login.html"),
                "initialUserHash" to ("secrets:S8215" to "src/main/resources/db/migration/V1__init.sql"),
                "railwayNpx" to ("githubactions:S6505" to ".github/workflows/ci.yml"),
                "gradleLockfile" to ("text:S8569" to "build.gradle.kts"),
                "gradleVerification" to ("kotlin:S6474" to "**"),
            )
        property("sonar.issue.ignore.multicriteria", ignored.keys.joinToString(","))
        ignored.forEach { (name, rule) ->
            property("sonar.issue.ignore.multicriteria.$name.ruleKey", rule.first)
            property("sonar.issue.ignore.multicriteria.$name.resourceKey", rule.second)
        }
        property("sonar.organization", "fuji-t88")
        // 公開の識別子で、秘密情報ではない。名前の「Key」で Gitleaks が API キーと誤検知する
        property("sonar.projectKey", "fuji-t88_tsumory") // gitleaks:allow
    }
}
