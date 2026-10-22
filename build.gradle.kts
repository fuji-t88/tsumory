plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.3"
    id("org.owasp.dependencycheck") version "13.0.0"
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
    implementation("com.anthropic:anthropic-java:2.68.0")
    implementation("org.webjars:bootstrap:5.3.8")
    implementation("org.webjars:webjars-locator-lite:1.1.4")
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
