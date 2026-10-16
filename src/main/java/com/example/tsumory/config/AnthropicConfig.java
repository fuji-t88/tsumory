package com.example.tsumory.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Claude API のクライアント。ClaudeService（日記）と PostCategoryClassifier（カテゴリ分類）が共有する。タイムアウトはここで日記に合わせ、
 * 分類は呼び出しごとに短いタイムアウトで上書きする（phase2-spec.md の「7. AI 連携（カテゴリ分類） > 呼び出し」）。
 */
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class AnthropicConfig {

    /**
     * API キーは Spring の Environment 経由で読み、キーがなければ起動しない（CLAUDE.md の「AI（Claude API）」）。失敗してもリトライしない
     * （phase1-spec.md の「6. AI 連携 > 呼び出し」）。
     */
    @Bean(destroyMethod = "close")
    public AnthropicClient anthropicClient(@Value("${ANTHROPIC_API_KEY}") String apiKey, ClaudeProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(properties.timeout())
                .maxRetries(0)
                .build();
    }
}
