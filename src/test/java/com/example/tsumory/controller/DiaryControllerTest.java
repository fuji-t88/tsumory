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
import com.example.tsumory.service.DiaryGenerationException;
import com.example.tsumory.service.DiaryGenerationService;
import com.example.tsumory.service.DiaryNotFoundException;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.NoPostsException;
import com.example.tsumory.service.PostService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiaryController.class)
@Import({SecurityConfig.class, CustomUserDetailsService.class, DiaryControllerTest.FixedClock.class})
class DiaryControllerTest {

    private static final Long USER_ID = 1L;
    // 2026-10-07 21:00（日本時間）
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final CustomUserDetails PRINCIPAL = new CustomUserDetails(USER_ID, "demo", "$2a$10$hash");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, PostService.ZONE);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryService diaryService;

    @MockitoBean
    private DiaryGenerationService diaryGenerationService;

    @MockitoBean
    private UserRepository userRepository;

    private final User user = new User("demo", "hash", Instant.EPOCH);

    @BeforeEach
    void setUp() {
        when(diaryService.findByDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(diaryService.findByDate(USER_ID, YESTERDAY)).thenReturn(Optional.empty());
        when(diaryService.findByDate(USER_ID, TODAY.minusDays(2)))
                .thenThrow(new DiaryNotFoundException(TODAY.minusDays(2)));
        when(diaryService.findByDate(USER_ID, TODAY.plusDays(1)))
                .thenThrow(new DiaryNotFoundException(TODAY.plusDays(1)));
    }

    // --- 日記表示（view） ---

    @Test
    void 未ログインならログイン画面へリダイレクト() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 日記があれば本文と日本時間の作成時刻と作り直すボタンを出す() throws Exception {
        // 12:30Z は日本時間の 21:30
        when(diaryService.findByDate(USER_ID, TODAY))
                .thenReturn(Optional.of(new Diary(user, TODAY, "寝坊した一日。", Instant.parse("2026-10-07T12:30:00Z"))));

        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(view().name("diary"))
                .andExpect(content().string(containsString("2026年10月7日（水）の日記")))
                .andExpect(content().string(containsString("寝坊した一日。")))
                .andExpect(content().string(containsString("21:30 に作成")))
                .andExpect(content().string(containsString(">作り直す</button>")));
    }

    @Test
    void 日記がなければ日記を書くボタンを出す() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("まだ日記はありません")))
                .andExpect(content().string(containsString(">日記を書く</button>")))
                .andExpect(content().string(not(containsString(">作り直す</button>"))));
    }

    @Test
    void 生成中のローディング表示と二重押下防止のスクリプトを読み込む() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("src=\"/js/diary-generate.js\"")))
                .andExpect(content().string(containsString("id=\"diary-loading\"")))
                .andExpect(content().string(containsString("role=\"status\" aria-live=\"polite\" hidden")))
                .andExpect(content().string(containsString("日記を書いています。1 分ほどかかることがあります。このままお待ちください")))
                .andExpect(content().string(containsString("class=\"js-diary-generate\"")))
                // インラインのスクリプトは書かない
                .andExpect(content().string(not(containsString("onsubmit"))));
    }

    @Test
    void 昨日の日記も表示できる() throws Exception {
        // 14:00Z は日本時間の 23:00
        when(diaryService.findByDate(USER_ID, YESTERDAY))
                .thenReturn(Optional.of(new Diary(user, YESTERDAY, "昨日の日記", Instant.parse("2026-10-06T14:00:00Z"))));

        mockMvc.perform(get("/diary/2026-10-06").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("昨日の日記")))
                .andExpect(content().string(containsString("23:00 に作成")));
    }

    @Test
    void 今日以外の日記には書く作り直すボタンを出さず日記一覧を選択中にする() throws Exception {
        // 書く・作り直すのは今日の日記だけなので、過去の日の画面から押せないようにする
        when(diaryService.findByDate(USER_ID, YESTERDAY))
                .thenReturn(Optional.of(new Diary(user, YESTERDAY, "昨日の日記", Instant.parse("2026-10-06T14:00:00Z"))));

        mockMvc.perform(get("/diary/2026-10-06").with(user(PRINCIPAL)))
                .andExpect(content().string(not(containsString(">作り直す</button>"))))
                .andExpect(content().string(not(containsString("action=\"/diary/generate\""))))
                .andExpect(content().string(containsString("href=\"/diaries\" class=\"nav-link active\"")));
        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("href=\"/\" class=\"nav-link active\"")));
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
    void フラッシュメッセージがあれば赤で出す() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07")
                        .with(user(PRINCIPAL))
                        .flashAttr("message", DiaryController.GENERATION_FAILED_MESSAGE))
                .andExpect(content().string(containsString("alert-danger")))
                .andExpect(content().string(containsString(DiaryController.GENERATION_FAILED_MESSAGE)));
    }

    // --- 日記生成（generate） ---

    @Test
    void 生成に成功したら今日の日記表示へ() throws Exception {
        when(diaryGenerationService.generateToday(USER_ID)).thenReturn(new Diary(user, TODAY, "日記", NOW));

        mockMvc.perform(post("/diary/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/diary/2026-10-07"))
                .andExpect(flash().attributeCount(0));
    }

    @Test
    void 生成中に0時をまたいだら保存された日記の日付へ() throws Exception {
        // 押したときは 10/7（Clock）だが、保存は 10/8 の日記として行われた場合
        var tomorrow = TODAY.plusDays(1);
        when(diaryGenerationService.generateToday(USER_ID)).thenReturn(new Diary(user, tomorrow, "日記", NOW));

        mockMvc.perform(post("/diary/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/diary/2026-10-08"));
    }

    @Test
    void つぶやきが0件ならメッセージを付けてホームへ() throws Exception {
        when(diaryGenerationService.generateToday(USER_ID)).thenThrow(new NoPostsException());

        mockMvc.perform(post("/diary/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", DiaryController.NO_POSTS_MESSAGE));
    }

    @Test
    void AIの呼び出しが失敗したらメッセージを付けて今日の日記表示へ() throws Exception {
        when(diaryGenerationService.generateToday(USER_ID)).thenThrow(new DiaryGenerationException("failed"));

        mockMvc.perform(post("/diary/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/diary/2026-10-07"))
                .andExpect(flash().attribute("message", DiaryController.GENERATION_FAILED_MESSAGE));
    }

    @Test
    void CSRFトークンなしの生成は403() throws Exception {
        mockMvc.perform(post("/diary/generate").with(user(PRINCIPAL))).andExpect(status().isForbidden());

        verify(diaryGenerationService, never()).generateToday(any());
    }

    @Test
    void 未ログインの生成はログイン画面へ() throws Exception {
        mockMvc.perform(post("/diary/generate").with(csrf())).andExpect(redirectedUrl("/login"));

        verify(diaryGenerationService, never()).generateToday(any());
    }
}
