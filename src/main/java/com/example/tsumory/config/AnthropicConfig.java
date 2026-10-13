package com.example.tsumory.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Claude API を使うとき（tsumory.diary-writer=claude）だけクライアントを作る。stub なら API キーがなくても起動できる
@Configuration
@ConditionalOnProperty(name = "tsumory.diary-writer", havingValue = "claude")
public class AnthropicConfig {

    /** API キーは環境変数 ANTHROPIC_API_KEY から読む。失敗してもリトライしない（仕様 6 章）。 */
    @Bean(destroyMethod = "close")
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(Duration.ofSeconds(120))
                .maxRetries(0)
                .build();
    }
}
