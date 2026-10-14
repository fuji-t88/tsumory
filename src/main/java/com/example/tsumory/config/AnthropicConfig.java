package com.example.tsumory.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Claude API のクライアント。ClaudeService が使う
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class AnthropicConfig {

    /**
     * API キーは環境変数 ANTHROPIC_API_KEY から Spring の Environment 経由で読む（SDK の fromEnv() は System.getenv() を直接読み、
     * .env などを見ないため使わない）。キーがなければ起動しない。失敗してもリトライしない（仕様 6 章）。
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
