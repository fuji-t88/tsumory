package com.example.tsumory.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエストごとにリクエスト ID とログイン中のユーザー ID を MDC に入れ、そのリクエストのログすべてに付ける（CLAUDE.md の「リクエストの追跡」）。
 *
 * <p>{@code SecurityConfig} で Spring Security のチェーンに入れる。{@code @Component} にはしない（サーブレットのフィルターとしても登録され、2 回動くため）。
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    static final String REQUEST_ID = "requestId";
    static final String USER_ID = "userId";
    static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final long NANOS_PER_MILLI = 1_000_000;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/webjars/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // クライアントから送られた ID は使わない（ログに任意の文字列を書き込ませないため）
        var requestId = UUID.randomUUID().toString();
        MDC.put(REQUEST_ID, requestId);
        currentUserId().ifPresent(userId -> MDC.put(USER_ID, userId.toString()));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        var startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            // MDC が残っているうちに出す。ここで出さないと、例外のログからリクエストとユーザーをたどれない
            log.error(
                    "request failed: method={}, uri={}, elapsedMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    elapsedMs(startedAt),
                    e);
            if (response.isCommitted()) {
                throw e;
            }
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
        } finally {
            // ログイン直後はここで初めてユーザーが分かるので、終了のログには付け直す
            currentUserId().ifPresent(userId -> MDC.put(USER_ID, userId.toString()));
            log.info(
                    "request completed: method={}, uri={}, status={}, elapsedMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs(startedAt));
            MDC.remove(REQUEST_ID);
            MDC.remove(USER_ID);
        }
    }

    private static Optional<Long> currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user) {
            return Optional.of(user.userId());
        }
        return Optional.empty();
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / NANOS_PER_MILLI;
    }
}
