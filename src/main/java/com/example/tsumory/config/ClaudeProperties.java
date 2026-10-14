package com.example.tsumory.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Claude API の呼び出しの設定（application.yaml の anthropic.*）。値が不正ならアプリを起動しない。API キーは持たない。
 *
 * <p>{@code model}・{@code effort}・{@code maxTokens} は日記の生成、{@code classification} はつぶやきのカテゴリ分類の設定。
 * {@code timeout} はクライアント全体で共有する。
 */
@ConfigurationProperties(prefix = "anthropic")
@Validated
public record ClaudeProperties(
        @NotBlank String model,

        @NotNull @Pattern(regexp = "low|medium|high|xhigh|max")
        String effort,

        @Min(1) int maxTokens,
        @NotNull Duration timeout,
        @NotNull @Valid Classification classification) {

    /**
     * つぶやきのカテゴリ分類の設定（application.yaml の anthropic.classification.*）。
     *
     * <p>{@code timeout} は分類の呼び出しだけに使う（投稿・編集の応答が分類を待つので、クライアント全体の {@code anthropic.timeout} より短くする）。
     */
    public record Classification(
            @NotBlank String model,

            @NotNull @Pattern(regexp = "low|medium|high|xhigh|max")
            String effort,

            @Min(1) int maxTokens,
            @NotNull Duration timeout) {}
}
