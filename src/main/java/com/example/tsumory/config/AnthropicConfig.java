package com.example.tsumory.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;

// Claude API のクライアント。まだ Bean にしていない（Claude につなぐまでは StubDiaryWriter を使い、API キーなしで起動できるようにする）。
// Claude につなぐときは @Configuration を付ける（DiaryAiService の Javadoc を参照）
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
