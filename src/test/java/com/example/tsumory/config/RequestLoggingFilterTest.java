package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void リクエストの間はリクエストIDとユーザーIDがMDCに入り終わると消える() throws Exception {
        var user = new CustomUserDetails(42L, "demo", "hash");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        Map<String, String> seen = new HashMap<>();
        FilterChain chain = (req, res) -> seen.putAll(MDC.getCopyOfContextMap());

        filter.doFilter(new MockHttpServletRequest("GET", "/"), response, chain);

        assertThat(seen.get("requestId")).isNotBlank().isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(seen.get("userId")).isEqualTo("42");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void 未ログインならユーザーIDを付けない() throws Exception {
        Map<String, String> seen = new HashMap<>();
        FilterChain chain = (req, res) -> seen.putAll(MDC.getCopyOfContextMap());

        filter.doFilter(new MockHttpServletRequest("GET", "/login"), response, chain);

        assertThat(seen).containsKey("requestId").doesNotContainKey("userId");
    }

    @Test
    void 安全な書式のリクエストIDは引き継いで応答にも返す() throws Exception {
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader("X-Request-Id", "proxy-1a2b.3c_4d");
        Map<String, String> seen = new HashMap<>();

        filter.doFilter(request, response, (req, res) -> seen.putAll(MDC.getCopyOfContextMap()));

        assertThat(seen.get("requestId")).isEqualTo("proxy-1a2b.3c_4d");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("proxy-1a2b.3c_4d");
    }

    @ParameterizedTest
    @ValueSource(strings = {"injected\nfake log line", "has space", "a<b>", ""})
    void 安全でない書式のリクエストIDは使わず新しく作る(String received) throws Exception {
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader("X-Request-Id", received);

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader("X-Request-Id")).isNotEqualTo(received).matches("[0-9a-f-]{36}");
    }

    @Test
    void 長すぎるリクエストIDは使わない() {
        assertThat(RequestLoggingFilter.resolveRequestId("a".repeat(64))).isEqualTo("a".repeat(64));
        assertThat(RequestLoggingFilter.resolveRequestId("a".repeat(65))).isNotEqualTo("a".repeat(65));
    }

    @Test
    void ヘッダーがなければ新しく作る() {
        assertThat(RequestLoggingFilter.resolveRequestId(null)).matches("[0-9a-f-]{36}");
    }

    @Test
    void Spring_Securityのフィルターの直後に動く() {
        var order = RequestLoggingFilter.class.getAnnotation(Order.class);

        assertThat(order.value()).isEqualTo(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
    }

    @Test
    void 前のリクエストで入れた値は次のリクエストに残らない() throws Exception {
        var user = new CustomUserDetails(42L, "demo", "hash");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        filter.doFilter(new MockHttpServletRequest("GET", "/"), response, (req, res) -> {});
        SecurityContextHolder.clearContext();
        Map<String, String> seen = new HashMap<>();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/"),
                new MockHttpServletResponse(),
                (req, res) -> seen.putAll(MDC.getCopyOfContextMap()));

        assertThat(seen).doesNotContainKey("userId");
    }

    @Test
    void ほかの部品がMDCに入れた値は消さない() throws Exception {
        MDC.put("other", "keep");
        try {
            filter.doFilter(new MockHttpServletRequest("GET", "/"), response, (req, res) -> {});

            assertThat(MDC.get("other")).isEqualTo("keep");
        } finally {
            MDC.remove("other");
        }
    }

    @Test
    void リクエストごとに別のIDになる() throws Exception {
        var other = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/"), response, (req, res) -> {});
        filter.doFilter(new MockHttpServletRequest("GET", "/"), other, (req, res) -> {});

        assertThat(response.getHeader("X-Request-Id")).isNotEqualTo(other.getHeader("X-Request-Id"));
    }

    @Test
    void 処理されなかった例外は500にしてMDCを消す() throws Exception {
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        filter.doFilter(new MockHttpServletRequest("POST", "/diary/generate"), response, chain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void 静的リソースは対象にしない() throws Exception {
        filter.doFilter(
                new MockHttpServletRequest("GET", "/webjars/bootstrap/css/bootstrap.min.css"),
                response,
                (req, res) -> {});

        assertThat(response.getHeader("X-Request-Id")).isNull();
    }
}
