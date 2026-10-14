package com.example.tsumory.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Claude API の呼び出しの設定（application.yaml の anthropic.*）。値が不正ならアプリを起動しない。API キーは持たない。 */
@ConfigurationProperties(prefix = "anthropic")
@Validated
public record ClaudeProperties(
        @NotBlank String model,

        @NotNull @Pattern(regexp = "low|medium|high|xhigh|max")
        String effort,

        @Min(1) int maxTokens,
        @NotNull Duration timeout) {}
