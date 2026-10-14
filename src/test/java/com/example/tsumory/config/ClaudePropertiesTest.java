package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ClaudePropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ClaudeProperties.class)
    static class TestConfig {}

    private static final String[] VALID = {
        "anthropic.model=claude-opus-5-5",
        "anthropic.effort=medium",
        "anthropic.max-tokens=16000",
        "anthropic.timeout=120s",
        "anthropic.classification.model=claude-opus-5-5",
        "anthropic.classification.effort=low",
        "anthropic.classification.max-tokens=2000",
        "anthropic.classification.timeout=10s"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(VALID);

    @Test
    void 設定値がバインドされる() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(ClaudeProperties.class);
            assertThat(properties.model()).isEqualTo("claude-opus-5-5");
            assertThat(properties.effort()).isEqualTo("medium");
            assertThat(properties.maxTokens()).isEqualTo(16000);
            assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(120));
            assertThat(properties.classification())
                    .isEqualTo(new ClaudeProperties.Classification(
                            "claude-opus-5-5", "low", 2000, Duration.ofSeconds(10)));
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "anthropic.model=",
                "anthropic.max-tokens=0",
                "anthropic.effort=extreme",
                "anthropic.timeout=",
                "anthropic.classification.model=",
                "anthropic.classification.effort=none",
                "anthropic.classification.max-tokens=0",
                "anthropic.classification.timeout="
            })
    void 不正な値では起動しない(String invalid) {
        runner.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
    }
}
