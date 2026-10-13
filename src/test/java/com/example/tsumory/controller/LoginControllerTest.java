package com.example.tsumory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.config.CustomUserDetailsService;
import com.example.tsumory.config.SecurityConfig;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({LoginController.class, PostController.class})
@Import({SecurityConfig.class, CustomUserDetailsService.class})
class LoginControllerTest {

    private static final String HASH = new BCryptPasswordEncoder().encode("password");

    @Autowired
    private MockMvc mockMvc;

    // CustomUserDetailsService は本物を使い、その下の UserRepository だけをモックにする
    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        var user = new User("demo", HASH, Instant.parse("2026-10-01T00:00:00Z"));
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
    }

    private static CustomUserDetails demoUser() {
        return new CustomUserDetails(1L, "demo", HASH);
    }

    @Test
    void 未ログインでトップを開くとログイン画面へリダイレクト() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void ログイン画面は未ログインでも表示でき_CSRFトークンを埋め込む() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void ログイン失敗のメッセージを表示する() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("ユーザー名またはパスワードが違います")));
    }

    @Test
    void ログアウト後のメッセージを表示する() throws Exception {
        mockMvc.perform(get("/login").param("logout", "")).andExpect(content().string(containsString("ログアウトしました")));
    }

    @Test
    void 正しいパスワードでログインするとトップへリダイレクト() throws Exception {
        mockMvc.perform(formLogin().user("demo").password("password"))
                .andExpect(authenticated().withUsername("demo"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void ログイン前に別のURLを開いていてもログイン後はトップへリダイレクト() throws Exception {
        var session = (MockHttpSession) mockMvc.perform(get("/diary/2026-10-07"))
                .andExpect(redirectedUrl("/login"))
                .andReturn()
                .getRequest()
                .getSession();

        mockMvc.perform(post("/login")
                        .param("username", "demo")
                        .param("password", "password")
                        .session(session)
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void ログインするとセッションIDが新しくなる() throws Exception {
        var session = new MockHttpSession();
        var before = session.getId();

        var after = mockMvc.perform(post("/login")
                        .param("username", "demo")
                        .param("password", "password")
                        .session(session)
                        .with(csrf()))
                .andExpect(authenticated())
                .andReturn()
                .getRequest()
                .getSession()
                .getId();

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void 静的リソースは未ログインでもログイン画面へ移らない() throws Exception {
        mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void 開放していないパスは未ログインならログイン画面へリダイレクト() throws Exception {
        for (var path : List.of("/images/logo.png", "/favicon.ico", "/error", "/posts", "/diary/2026-10-07")) {
            mockMvc.perform(get(path)).andExpect(redirectedUrl("/login"));
        }
    }

    @Test
    void ログイン失敗後の画面は未ログインでも表示できる() throws Exception {
        mockMvc.perform(get("/login?error")).andExpect(status().isOk());
        mockMvc.perform(get("/login?logout")).andExpect(status().isOk());
    }

    @Test
    void パスワードが違うとエラー付きでログイン画面へ戻る() throws Exception {
        mockMvc.perform(formLogin().user("demo").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void 存在しないユーザーでもエラー付きでログイン画面へ戻る() throws Exception {
        mockMvc.perform(formLogin().user("nobody").password("password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void CSRFトークンなしのログインは403() throws Exception {
        mockMvc.perform(post("/login").param("username", "demo").param("password", "password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ログアウトするとログイン画面へリダイレクト() throws Exception {
        mockMvc.perform(logout()).andExpect(unauthenticated()).andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void CSRFトークンなしのログアウトは403() throws Exception {
        mockMvc.perform(post("/logout").with(user(demoUser()))).andExpect(status().isForbidden());
    }

    @Test
    void ログイン後のトップにユーザー名とログアウトボタンを表示する() throws Exception {
        mockMvc.perform(get("/").with(user(demoUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">demo</span>")))
                .andExpect(content().string(containsString("ログアウト")));
    }
}
