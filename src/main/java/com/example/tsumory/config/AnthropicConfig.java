package com.example.tsumory.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Claude API のクライアント。DiaryAiService が使う
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class AnthropicConfig {

    /** API キーは環境変数 ANTHROPIC_API_KEY から読む。失敗してもリトライしない（仕様 6 章）。 */
    @Bean(destroyMethod = "close")
    public AnthropicClient anthropicClient(ClaudeProperties properties) {
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(properties.timeout())
                .maxRetries(0)
                .build();
    }
}
