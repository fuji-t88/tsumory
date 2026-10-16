package com.example.tsumory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.config.SecurityConfig;
import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webmvc.autoconfigure.error.DefaultErrorViewResolver;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

// MockMvc はサーブレットコンテナのエラー画面への切り替えを再現しない。そこで次の 3 つに分けて確かめる
// - 画面の中身：テスト用の controller からエラー画面を描画する
// - 状態コードごとの画面の選び分け：Spring Boot の DefaultErrorViewResolver を直接呼ぶ（このスライスには Bean がない）
// - 汎用の画面（error.html）：Spring Boot のエラー処理（/error）を、エラーの属性を付けて直接呼ぶ
@WebMvcTest(ErrorPageTest.RenderErrorPageController.class)
@Import({SecurityConfig.class, ErrorPageTest.RenderErrorPageController.class})
class ErrorPageTest {

    private static final AtomicReference<String> userIdSeenByController = new AtomicReference<>();

    private static final CustomUserDetails DEMO = new CustomUserDetails(1L, "demo", "hash");

    @Controller
    static class RenderErrorPageController {
        @GetMapping("/test/error-page")
        String render() {
            // RequestLoggingFilter が Spring Security のあとに動いていれば、ここでユーザー ID が MDC に入っている
            userIdSeenByController.set(MDC.get("userId"));
            return "error/500";
        }

        @GetMapping("/test/not-found-page")
        String renderNotFound() {
            return "error/404";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void エラー画面にレスポンスヘッダーと同じリクエストIDを出す() throws Exception {
        var result = mockMvc.perform(get("/test/error-page").with(user(DEMO)))
                .andExpect(status().isOk())
                .andReturn();

        var requestId = result.getResponse().getHeader("X-Request-Id");
        var html = result.getResponse().getContentAsString();
        assertThat(requestId).isNotBlank();
        assertThat(userIdSeenByController.get()).isEqualTo("1");
        assertThat(html).contains("問題が発生しました").contains("お問い合わせの際は").contains(requestId);
        // テンプレートのコメントは Thymeleaf のコメントで書き、ブラウザに送らない
        assertThat(html).doesNotContain("<!--");
    }

    @Test
    void 見つからない画面は仕様の文言と戻るボタンを出す() throws Exception {
        var html = mockMvc.perform(get("/test/not-found-page").with(user(DEMO)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("404")
                .contains("ページが見つかりません")
                .contains("つぶやきが削除されたか、URL が間違っている可能性があります。")
                .contains("今日のつぶやきへ戻る")
                // ヘッダーはサービス名だけ
                .doesNotContain("ログアウト");
    }

    @Test
    void 状態コードが404と500なら専用の画面を選び_それ以外は汎用の画面に任せる() {
        var resolver = new DefaultErrorViewResolver(applicationContext, new WebProperties.Resources());
        var request = new MockHttpServletRequest();

        assertThat(resolver.resolveErrorView(request, HttpStatus.NOT_FOUND, Map.of())
                        .getViewName())
                .isEqualTo("error/404");
        assertThat(resolver.resolveErrorView(request, HttpStatus.INTERNAL_SERVER_ERROR, Map.of())
                        .getViewName())
                .isEqualTo("error/500");
        // 専用の画面がなければ null。Spring Boot のエラー処理は error ビュー（templates/error.html）を使う
        assertThat(resolver.resolveErrorView(request, HttpStatus.METHOD_NOT_ALLOWED, Map.of()))
                .isNull();
        assertThat(resolver.resolveErrorView(request, HttpStatus.FORBIDDEN, Map.of()))
                .isNull();
    }

    @Test
    void 専用の画面がないエラーは日本語の汎用の画面を出し_内部情報は出さない() throws Exception {
        var result = mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 405)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/posts")
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "select * from posts")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("internal detail"))
                        .with(user(DEMO)))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();

        var html = result.getResponse().getContentAsString();
        assertThat(html)
                .contains("405")
                .contains("ページを表示できませんでした")
                .contains("今日のつぶやきへ戻る")
                .doesNotContain("Whitelabel")
                .doesNotContain("select * from posts")
                .doesNotContain("internal detail")
                .doesNotContain("IllegalStateException");
    }
}
