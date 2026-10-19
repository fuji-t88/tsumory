package com.example.tsumory.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 公開 URL のすべてを Basic 認証で守る。アプリのログインより手前の関門なので、Spring Security のフィルターより前に置く
 * （phase1-spec.md の「7. セキュリティ > 公開 URL の Basic 認証」）。
 */
@Slf4j
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)
public class BasicAuthFilter extends OncePerRequestFilter {

    static final String CHALLENGE = "Basic realm=\"Tsumory\", charset=\"UTF-8\"";
    private static final String SCHEME = "Basic ";

    private final boolean enabled;
    private final byte[] username;
    private final byte[] password;

    /**
     * ユーザー名とパスワードの両方があれば有効、どちらもなければ無効にする。
     *
     * @throws IllegalStateException 片方だけが設定されているとき、または空白だけの値が設定されているとき
     */
    public BasicAuthFilter(
            @Value("${BASIC_AUTH_USERNAME:}") String username, @Value("${BASIC_AUTH_PASSWORD:}") String password) {
        var hasUsername = !username.isEmpty();
        var hasPassword = !password.isEmpty();
        if (hasUsername != hasPassword) {
            throw new IllegalStateException("BASIC_AUTH_USERNAME and BASIC_AUTH_PASSWORD must be set together");
        }
        if (hasUsername && (username.isBlank() || password.isBlank())) {
            throw new IllegalStateException("BASIC_AUTH_USERNAME and BASIC_AUTH_PASSWORD must not be blank");
        }
        this.enabled = hasUsername;
        this.username = username.getBytes(UTF_8);
        this.password = password.getBytes(UTF_8);
        log.info("Basic auth for the public URL: {}", enabled ? "enabled" : "disabled");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            // ブラウザは最初に必ず資格情報なしで来るので、ログに出さない
            challenge(response);
            return;
        }
        if (!matches(authorization.substring(SCHEME.length()).strip())) {
            log.warn("Basic auth failed: method={}, uri={}", request.getMethod(), request.getRequestURI());
            challenge(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matches(String encoded) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            // 読めない値は違う資格情報として扱う。呼び出し側が WARN を出す
            return false;
        }
        var credentials = new String(decoded, UTF_8);
        var colon = credentials.indexOf(':');
        if (colon < 0) {
            return false;
        }
        // 短絡させず両方を比べる（どちらが違ったかを処理時間から推測されないように）
        var usernameMatches =
                MessageDigest.isEqual(username, credentials.substring(0, colon).getBytes(UTF_8));
        var passwordMatches =
                MessageDigest.isEqual(password, credentials.substring(colon + 1).getBytes(UTF_8));
        return usernameMatches & passwordMatches;
    }

    private static void challenge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("認証が必要です");
    }
}
