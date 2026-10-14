package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
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
    void クライアントが送ったリクエストIDは使わない() throws Exception {
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader("X-Request-Id", "injected\nfake log line");

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader("X-Request-Id")).doesNotContain("injected");
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
