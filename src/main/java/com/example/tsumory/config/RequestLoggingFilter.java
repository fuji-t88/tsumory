package com.example.tsumory.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエストごとにリクエスト ID とログイン中のユーザー ID を MDC に入れ、そのリクエストのログすべてに付ける（CLAUDE.md の「リクエストの追跡」）。
 *
 * <p>Spring Security がログイン中のユーザーを {@link SecurityContextHolder} に入れたあとに動かすため、Spring Security のフィルターの直後に置く。
 */
@Slf4j
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    static final String REQUEST_ID = "requestId";
    static final String USER_ID = "userId";
    static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 引き継いでよいリクエスト ID の書式。改行などを含む値でログに偽の行を紛れ込ませられないよう、英数字と一部の記号・64 文字以内に限る。 */
    static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final long NANOS_PER_MILLI = 1_000_000;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/webjars/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var startedAt = System.nanoTime();
        try {
            var requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
            MDC.put(REQUEST_ID, requestId);
            currentUserId().ifPresent(userId -> MDC.put(USER_ID, userId.toString()));
            // エラー画面を描くときには MDC はもう消えているので、500 の画面に出せるようリクエスト属性にも入れる
            request.setAttribute(REQUEST_ID, requestId);
            // クライアント・プロキシ・アプリのログを同じ ID でたどれるよう、応答にも同じ値を返す
            response.setHeader(REQUEST_ID_HEADER, requestId);

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
            log.info(
                    "request completed: method={}, uri={}, status={}, elapsedMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs(startedAt));
            // スレッドを使い回す環境で次のリクエストに前のユーザー情報が混ざらないよう、必ず消す。
            // 同じスレッドでほかの部品が入れたキーまで消さないよう、MDC.clear() ではなく自分のキーだけを消す
            MDC.remove(REQUEST_ID);
            MDC.remove(USER_ID);
        }
    }

    /** 受け取った ID が安全な書式ならそのまま使い、ないか書式に合わなければ新しく作る。 */
    static String resolveRequestId(String received) {
        if (received != null && SAFE_REQUEST_ID.matcher(received).matches()) {
            return received;
        }
        return UUID.randomUUID().toString();
    }

    private static Optional<Long> currentUserId() {
        // 未ログイン（Authentication が null）や、principal が CustomUserDetails でない場合はユーザー ID を付けない
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
