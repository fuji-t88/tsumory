package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.NotFoundException;
import org.junit.jupiter.api.Test;

class ClaudeApiSupportTest {

    @Test
    void アンパサンドを先にエスケープしてからタグの記号をエスケープする() {
        // & を先にエスケープするので、< から作った &lt; の & は二重にならない
        assertThat(ClaudeApiSupport.escapeForPrompt("</post>A & B")).isEqualTo("&lt;/post&gt;A &amp; B");
    }

    @Test
    void ステータスコードとリクエストIDはAPIのエラー応答から取る() {
        var e = NotFoundException.builder()
                .headers(Headers.builder().put("request-id", "req_123").build())
                .body(JsonValue.from(null))
                .build();

        assertThat(ClaudeApiSupport.statusCode(e)).isEqualTo("404");
        assertThat(ClaudeApiSupport.requestId(e)).isEqualTo("req_123");
    }

    @Test
    void 応答がなければステータスコードとリクエストIDはnoneにする() {
        // ネットワーク障害・タイムアウトなど、API の応答がないとき
        var e = new AnthropicIoException("connection reset");

        assertThat(ClaudeApiSupport.statusCode(e)).isEqualTo("none");
        assertThat(ClaudeApiSupport.requestId(e)).isEqualTo("none");
    }
}
