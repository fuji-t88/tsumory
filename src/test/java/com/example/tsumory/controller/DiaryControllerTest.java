package com.example.tsumory.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.config.CustomUserDetailsService;
import com.example.tsumory.config.SecurityConfig;
import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.UserRepository;
import com.example.tsumory.service.DiaryGenerationResult;
import com.example.tsumory.service.DiaryGenerationService;
import com.example.tsumory.service.DiaryItem;
import com.example.tsumory.service.DiaryNotFoundException;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.PostService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiaryController.class)
@Import({SecurityConfig.class, CustomUserDetailsService.class})
class DiaryControllerTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final CustomUserDetails PRINCIPAL = new CustomUserDetails(USER_ID, "demo", "$2a$10$hash");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryService diaryService;

    @MockitoBean
    private DiaryGenerationService diaryGenerationService;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        when(postService.today()).thenReturn(TODAY);
        when(diaryService.findDiary(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(diaryService.findDiary(USER_ID, YESTERDAY)).thenReturn(Optional.empty());
        when(diaryService.findDiary(USER_ID, TODAY.minusDays(2)))
                .thenThrow(new DiaryNotFoundException(TODAY.minusDays(2)));
        when(diaryService.findDiary(USER_ID, TODAY.plusDays(1)))
                .thenThrow(new DiaryNotFoundException(TODAY.plusDays(1)));
    }

    // --- 日記表示 ---

    @Test
    void 未ログインならログイン画面へリダイレクト() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 日記があれば本文と作成時刻と作り直すボタンを出す() throws Exception {
        when(diaryService.findDiary(USER_ID, TODAY))
                .thenReturn(Optional.of(new DiaryItem(TODAY, "寝坊した一日。", LocalTime.of(21, 30))));
        when(postService.countPosts(USER_ID, TODAY)).thenReturn(2L);

        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/show"))
                .andExpect(content().string(containsString("2026年10月7日（水）の日記")))
                .andExpect(content().string(containsString("寝坊した一日。")))
                .andExpect(content().string(containsString("21:30 に作成")))
                .andExpect(content().string(containsString(">作り直す</button>")))
                .andExpect(content().string(containsString("value=\"2026-10-07\"")));
    }

    @Test
    void つぶやきがあり日記がなければ件数と日記を書くボタンを出す() throws Exception {
        when(postService.countPosts(USER_ID, TODAY)).thenReturn(3L);

        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("その日のつぶやき 3 件から日記を書きます")))
                .andExpect(content().string(containsString("action=\"/diary/generate\"")))
                .andExpect(content().string(not(containsString(">作り直す</button>"))));
    }

    @Test
    void 今日のつぶやきが0件なら無効の日記を書くボタンを出す() throws Exception {
        when(postService.countPosts(USER_ID, TODAY)).thenReturn(0L);

        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("つぶやくと日記を書けます")))
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(not(containsString("action=\"/diary/generate\""))));
    }

    @Test
    void 昨日のつぶやきが0件ならボタンを出さない() throws Exception {
        when(postService.countPosts(USER_ID, YESTERDAY)).thenReturn(0L);

        mockMvc.perform(get("/diary/2026-10-06").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("この日のつぶやきはありません")))
                .andExpect(content().string(not(containsString("日記を書く"))));
    }

    @Test
    void つぶやきを直すリンクは今日なら今日のつぶやき昨日なら昨日の区画へ() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("href=\"/\"")));
        mockMvc.perform(get("/diary/2026-10-06").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("href=\"/#yesterday\"")));
    }

    @Test
    void 一昨日と明日の日記は404() throws Exception {
        mockMvc.perform(get("/diary/2026-10-05").with(user(PRINCIPAL))).andExpect(status().isNotFound());
        mockMvc.perform(get("/diary/2026-10-08").with(user(PRINCIPAL))).andExpect(status().isNotFound());
    }

    @Test
    void 形式が不正な日付は404() throws Exception {
        mockMvc.perform(get("/diary/2026-13-01").with(user(PRINCIPAL))).andExpect(status().isNotFound());
        mockMvc.perform(get("/diary/abc").with(user(PRINCIPAL))).andExpect(status().isNotFound());
        mockMvc.perform(get("/diary/2026-1-7").with(user(PRINCIPAL))).andExpect(status().isNotFound());
    }

    @Test
    void フラッシュメッセージを色ごとに出す() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)).flashAttr("successMessage", "日記を書きました"))
                .andExpect(content().string(containsString("alert-success")));
        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)).flashAttr("infoMessage", "いま日記を書いています"))
                .andExpect(content().string(containsString("alert-info")));
        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)).flashAttr("errorMessage", "日記を書けませんでした"))
                .andExpect(content().string(containsString("alert-danger")));
    }

    // --- 日記生成 ---

    @Test
    void 生成に成功したら成功のメッセージを付けて日記表示へ() throws Exception {
        var diary = new Diary(new User("demo", "hash", Instant.EPOCH), TODAY, "日記", Instant.EPOCH);
        when(diaryGenerationService.generate(USER_ID, TODAY)).thenReturn(new DiaryGenerationResult.Generated(diary));

        mockMvc.perform(post("/diary/generate")
                        .param("date", "2026-10-07")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/diary/2026-10-07"))
                .andExpect(flash().attribute("successMessage", DiaryController.GENERATED_MESSAGE));
    }

    @Test
    void 昨日の日記も生成できる() throws Exception {
        when(diaryGenerationService.generate(USER_ID, YESTERDAY)).thenReturn(new DiaryGenerationResult.Failed());

        mockMvc.perform(post("/diary/generate")
                        .param("date", "2026-10-06")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/diary/2026-10-06"));

        verify(diaryGenerationService).generate(USER_ID, YESTERDAY);
    }

    @Test
    void つぶやきが0件なら青のメッセージ() throws Exception {
        when(diaryGenerationService.generate(USER_ID, TODAY)).thenReturn(new DiaryGenerationResult.NoPosts());

        mockMvc.perform(post("/diary/generate")
                        .param("date", "2026-10-07")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(flash().attribute("infoMessage", DiaryController.NO_POSTS_MESSAGE));
    }

    @Test
    void 生成中なら青のメッセージ() throws Exception {
        when(diaryGenerationService.generate(USER_ID, TODAY)).thenReturn(new DiaryGenerationResult.InProgress());

        mockMvc.perform(post("/diary/generate")
                        .param("date", "2026-10-07")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(flash().attribute("infoMessage", DiaryController.IN_PROGRESS_MESSAGE));
    }

    @Test
    void AIが拒否したら赤のメッセージ() throws Exception {
        when(diaryGenerationService.generate(USER_ID, TODAY)).thenReturn(new DiaryGenerationResult.Refused());

        mockMvc.perform(post("/diary/generate")
                        .param("date", "2026-10-07")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(flash().attribute("errorMessage", DiaryController.REFUSED_MESSAGE));
    }

    @Test
    void APIエラーなら赤のメッセージ() throws Exception {
        when(diaryGenerationService.generate(USER_ID, TODAY)).thenReturn(new DiaryGenerationResult.Failed());

        mockMvc.perform(post("/diary/generate")
                        .param("date", "2026-10-07")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(flash().attribute("errorMessage", DiaryController.FAILED_MESSAGE));
    }

    @Test
    void 一昨日の日付の生成は404() throws Exception {
        var old = TODAY.minusDays(2);
        when(diaryGenerationService.generate(USER_ID, old)).thenThrow(new DiaryNotFoundException(old));

        mockMvc.perform(post("/diary/generate")
                        .param("date", "2026-10-05")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 日付がないか形式が不正な生成は404で生成しない() throws Exception {
        mockMvc.perform(post("/diary/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/diary/generate")
                        .param("date", "abc")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(diaryGenerationService, never()).generate(any(), any());
    }

    @Test
    void CSRFトークンなしの生成は403() throws Exception {
        mockMvc.perform(post("/diary/generate").param("date", "2026-10-07").with(user(PRINCIPAL)))
                .andExpect(status().isForbidden());

        verify(diaryGenerationService, never()).generate(any(), any());
    }
}
