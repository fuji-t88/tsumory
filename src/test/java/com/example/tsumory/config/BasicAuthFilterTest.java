package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BasicAuthFilterTest {

    private static final String USERNAME = "guest";
    private static final String PASSWORD = "open-sesame";

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicBoolean passed = new AtomicBoolean();
    private final FilterChain chain = (req, res) -> passed.set(true);

    @Test
    void 環境変数がなければ資格情報なしで通す() throws Exception {
        var filter = new BasicAuthFilter("", "");

        filter.doFilter(new MockHttpServletRequest("GET", "/login"), response, chain);

        assertThat(passed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 有効なとき資格情報がなければ401と認証の求めを返して先に進まない() throws Exception {
        var filter = new BasicAuthFilter(USERNAME, PASSWORD);

        filter.doFilter(new MockHttpServletRequest("GET", "/login"), response, chain);

        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"Tsumory\", charset=\"UTF-8\"");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("認証が必要です");
    }

    @Test
    void ログイン画面だけでなく静的リソースも守る() throws Exception {
        var filter = new BasicAuthFilter(USERNAME, PASSWORD);

        filter.doFilter(new MockHttpServletRequest("GET", "/webjars/bootstrap/css/bootstrap.min.css"), response, chain);

        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 正しいユーザー名とパスワードなら通す() throws Exception {
        var filter = new BasicAuthFilter(USERNAME, PASSWORD);

        filter.doFilter(requestWith(basic(USERNAME + ":" + PASSWORD)), response, chain);

        assertThat(passed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 認証方式の名前は大文字と小文字を区別しない() throws Exception {
        var filter = new BasicAuthFilter(USERNAME, PASSWORD);

        filter.doFilter(requestWith(basic(USERNAME + ":" + PASSWORD).replace("Basic", "basic")), response, chain);

        assertThat(passed).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"guest:wrong", "other:open-sesame", "guest:", ":open-sesame", "guest:open-sesame-", "guest"})
    void ユーザー名かパスワードが違えば401で先に進まない(String credentials) throws Exception {
        var filter = new BasicAuthFilter(USERNAME, PASSWORD);

        filter.doFilter(requestWith(basic(credentials)), response, chain);

        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer abc", "Basic", "Basic not-base64!!", "Basicguest:open-sesame"})
    void Basicでない認証方式やBase64として読めない値は401で先に進まない(String header) throws Exception {
        var filter = new BasicAuthFilter(USERNAME, PASSWORD);

        filter.doFilter(requestWith(header), response, chain);

        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void コロンを含むパスワードと日本語のパスワードもUTF8で正しく比べる() throws Exception {
        var filter = new BasicAuthFilter("ゲスト", "ひらけ:ごま");

        filter.doFilter(requestWith(basic("ゲスト:ひらけ:ごま")), response, chain);

        assertThat(passed).isTrue();
    }

    @Test
    void ユーザー名とパスワードの片方だけでは作れない() {
        assertThatThrownBy(() -> new BasicAuthFilter(USERNAME, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(USERNAME);
        assertThatThrownBy(() -> new BasicAuthFilter("", PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(PASSWORD);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"'   '|open-sesame", "guest|'   '", "'   '|'   '", "'\t'|open-sesame"})
    void 空白だけのユーザー名かパスワードでは作れない(String username, String password) {
        assertThatThrownBy(() -> new BasicAuthFilter(username, password))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(USERNAME)
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    void SpringSecurityのフィルターより前に動く() {
        var order = BasicAuthFilter.class.getAnnotation(Order.class).value();

        assertThat(order).isLessThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
    }

    private static MockHttpServletRequest requestWith(String authorization) {
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader("Authorization", authorization);
        return request;
    }

    private static String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
