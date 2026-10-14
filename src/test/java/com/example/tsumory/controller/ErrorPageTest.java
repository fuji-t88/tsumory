package com.example.tsumory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.config.SecurityConfig;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

// MockMvc はサーブレットコンテナのエラー画面への切り替えを再現しないので、テスト用の controller から error/500 を描画して、
// RequestLoggingFilter が付けたリクエスト ID が画面に出ることを確かめる
@WebMvcTest(ErrorPageTest.RenderErrorPageController.class)
@Import({SecurityConfig.class, ErrorPageTest.RenderErrorPageController.class})
class ErrorPageTest {

    private static final AtomicReference<String> userIdSeenByController = new AtomicReference<>();

    @Controller
    static class RenderErrorPageController {
        @GetMapping("/test/error-page")
        String render() {
            // RequestLoggingFilter が Spring Security のあとに動いていれば、ここでユーザー ID が MDC に入っている
            userIdSeenByController.set(MDC.get("userId"));
            return "error/500";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void エラー画面にレスポンスヘッダーと同じリクエストIDを出す() throws Exception {
        var result = mockMvc.perform(get("/test/error-page").with(user(new CustomUserDetails(1L, "demo", "hash"))))
                .andExpect(status().isOk())
                .andReturn();

        var requestId = result.getResponse().getHeader("X-Request-Id");
        var html = result.getResponse().getContentAsString();
        assertThat(requestId).isNotBlank();
        assertThat(userIdSeenByController.get()).isEqualTo("1");
        assertThat(html).contains("問題が発生しました").contains("お問い合わせの際は").contains(requestId);
    }
}
