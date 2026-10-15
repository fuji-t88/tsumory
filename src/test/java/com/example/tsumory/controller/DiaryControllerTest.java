package com.example.tsumory.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.UserRepository;
import com.example.tsumory.service.DiaryGenerationException;
import com.example.tsumory.service.DiaryGenerationService;
import com.example.tsumory.service.DiaryNotFoundException;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.InvalidDiaryDateException;
import com.example.tsumory.service.NoPostsException;
import com.example.tsumory.service.PostItem;
import com.example.tsumory.service.PostService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
    private PostService postService;

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
        when(postService.isEditableDate(TODAY)).thenReturn(true);
        when(postService.isEditableDate(YESTERDAY)).thenReturn(true);
    }

    // --- この日のつぶやき ---

    @Test
    void この日のつぶやきを時刻と本文とカテゴリ付きで古い順に出す() throws Exception {
        when(postService.findPosts(USER_ID, TODAY))
                .thenReturn(List.of(
                        new PostItem(11L, "寝坊した", LocalTime.of(8, 12), true, PostCategory.OTHER),
                        new PostItem(10L, "会議が長引いた", LocalTime.of(12, 40), false, PostCategory.WORK)));

        var html = mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("この日のつぶやき")
                .containsSubsequence("08:12", "寝坊した", "その他", "12:40", "会議が長引いた", "仕事")
                .contains("badge bg-primary")
                // 本文は読むだけ。編集・削除の操作と「編集済み」は出さない
                .doesNotContain("/posts/11/edit")
                .doesNotContain("/posts/11/delete")
                .doesNotContain("編集済み");
    }

    @Test
    void つぶやきごとにこの日へ戻るカテゴリ変更フォームを出す() throws Exception {
        when(postService.findPosts(USER_ID, YESTERDAY))
                .thenReturn(List.of(new PostItem(10L, "会議が長引いた", LocalTime.of(12, 40), false, PostCategory.WORK)));

        var html = mockMvc.perform(get("/diary/2026-10-06").with(user(PRINCIPAL)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("action=\"/posts/10/category\"")
                .contains("<input type=\"hidden\" name=\"date\" value=\"2026-10-06\">")
                .contains("<label for=\"category-10\" class=\"visually-hidden\">カテゴリ</label>")
                .containsPattern("<option value=\"WORK\"\\s+selected=\"selected\">仕事</option>")
                // JavaScript なしでも動くよう、選んだだけでは送信せず「変更」で送る
                .contains(">変更</button>")
                .doesNotContain("onchange")
                .containsPattern("action=\"/posts/10/category\"[\\s\\S]*?name=\"_csrf\"");
    }

    @Test
    void この日のつぶやきがなければその旨を出す() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("この日のつぶやきはありません")));
    }

    @Test
    void つぶやきを直す導線は今日と昨日だけに出す() throws Exception {
        var old = TODAY.minusDays(5);
        when(diaryService.findByDate(USER_ID, old)).thenReturn(Optional.empty());

        mockMvc.perform(get("/diary/2026-10-06").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("つぶやきを直す")));
        // 一昨日より前の日は、その日のつぶやきを直せないので出さない
        mockMvc.perform(get("/diary/2026-10-02").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("つぶやきを直す"))));
    }

    // --- 日記表示（view） ---

    @Test
    void 未ログインならログイン画面へリダイレクト() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 日記があれば本文と日本時間の作成時刻と生成し直すボタンを出す() throws Exception {
        // 12:30Z は日本時間の 21:30
        when(diaryService.findByDate(USER_ID, TODAY))
                .thenReturn(Optional.of(new Diary(user, TODAY, "寝坊した一日。", Instant.parse("2026-10-07T12:30:00Z"))));

        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(view().name("diary"))
                .andExpect(content().string(containsString("2026年10月7日（水）の日記")))
                .andExpect(content().string(containsString("寝坊した一日。")))
                .andExpect(content().string(containsString("21:30 に作成")))
                .andExpect(content().string(containsString(">生成し直す</button>")));
    }

    private void stubPosts(LocalDate date, int count) {
        var posts = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new PostItem((long) i, "つぶやき" + i, LocalTime.of(9, i), false, PostCategory.OTHER))
                .toList();
        when(postService.findPosts(USER_ID, date)).thenReturn(posts);
    }

    @Test
    void 日記がなくつぶやきがあれば件数と日記を生成ボタンを出す() throws Exception {
        stubPosts(TODAY, 3);

        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("この日の日記はまだありません")))
                .andExpect(content().string(containsString("この日のつぶやき 3 件から日記を書きます")))
                .andExpect(content().string(containsString("action=\"/diary/2026-10-07/generate\"")))
                .andExpect(content().string(containsString(">日記を生成</button>")))
                .andExpect(content().string(not(containsString(">生成し直す</button>"))));
    }

    @Test
    void 日記もつぶやきもなければボタンを出さない() throws Exception {
        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("この日の日記はまだありません")))
                .andExpect(content().string(containsString("この日のつぶやきはありません")))
                .andExpect(content().string(not(containsString(">日記を生成</button>"))))
                .andExpect(content().string(not(containsString("/generate\""))));
    }

    @Test
    void 日記があればつぶやきが0件でも生成し直すボタンを出す() throws Exception {
        // つぶやきを全部消した日も「生成し直す」は出す（押すとメッセージが出て、日記はそのまま残る）
        when(diaryService.findByDate(USER_ID, TODAY))
                .thenReturn(Optional.of(new Diary(user, TODAY, "寝坊した一日。", Instant.parse("2026-10-07T12:30:00Z"))));

        mockMvc.perform(get("/diary/2026-10-07").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString(">生成し直す</button>")))
                .andExpect(content().string(containsString("action=\"/diary/2026-10-07/generate\"")));
    }

    @Test
    void 生成中のローディング表示と二重押下防止のスクリプトを読み込む() throws Exception {
        stubPosts(TODAY, 1);

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
    void 過去の日の画面でもその日の日記を生成と生成し直すボタンを出し日記一覧を選択中にする() throws Exception {
        // 遡って書いた日記：作成日が日記の日付と違うので「M月d日 HH:mm に作成」
        var old = TODAY.minusDays(5);
        when(diaryService.findByDate(USER_ID, old))
                .thenReturn(Optional.of(new Diary(user, old, "先週の日記", Instant.parse("2026-10-07T12:30:00Z"))));

        mockMvc.perform(get("/diary/2026-10-02").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString(">生成し直す</button>")))
                .andExpect(content().string(containsString("action=\"/diary/2026-10-02/generate\"")))
                .andExpect(content().string(containsString("10月7日 21:30 に作成")))
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
    void 生成に成功したらその日の日記表示へ() throws Exception {
        var past = LocalDate.of(2026, 10, 2);
        when(diaryGenerationService.generateForDate(USER_ID, past)).thenReturn(new Diary(user, past, "日記", NOW));

        mockMvc.perform(post("/diary/2026-10-02/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/diary/2026-10-02"))
                .andExpect(flash().attributeCount(0));
    }

    @Test
    void つぶやきが0件ならメッセージを付けてホームへ() throws Exception {
        when(diaryGenerationService.generateForDate(USER_ID, YESTERDAY)).thenThrow(new NoPostsException());

        mockMvc.perform(post("/diary/2026-10-06/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", "つぶやきがないため日記を生成できません"));
    }

    @Test
    void 未来の日付ならメッセージを付けてホームへ() throws Exception {
        when(diaryGenerationService.generateForDate(USER_ID, TODAY.plusDays(1)))
                .thenThrow(new InvalidDiaryDateException(TODAY.plusDays(1)));

        mockMvc.perform(post("/diary/2026-10-08/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", "未来の日付の日記は表示できません"));
    }

    @Test
    void AIの呼び出しが失敗したらメッセージを付けてその日の日記表示へ() throws Exception {
        when(diaryGenerationService.generateForDate(USER_ID, TODAY)).thenThrow(new DiaryGenerationException("failed"));

        mockMvc.perform(post("/diary/2026-10-07/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/diary/2026-10-07"))
                .andExpect(flash().attribute("message", DiaryController.GENERATION_FAILED_MESSAGE));
    }

    @Test
    void 登録日より前の日付と形式が不正な日付の生成は404() throws Exception {
        var beforeRegistration = LocalDate.of(2026, 9, 1);
        when(diaryGenerationService.generateForDate(USER_ID, beforeRegistration))
                .thenThrow(new DiaryNotFoundException(beforeRegistration));

        mockMvc.perform(post("/diary/2026-09-01/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/diary/abc/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 日付のない古い生成のURLは生成せずに405() throws Exception {
        // POST /diary/generate は削除した。古い画面を開いたままのタブから送られた場合。
        // GET /diary/{date} の形に当てはまるので、Spring MVC は 404 ではなく 405（POST は受け付けない）を返す
        mockMvc.perform(post("/diary/generate").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());

        verify(diaryGenerationService, never()).generateForDate(any(), any());
    }

    @Test
    void CSRFトークンなしの生成は403() throws Exception {
        mockMvc.perform(post("/diary/2026-10-07/generate").with(user(PRINCIPAL)))
                .andExpect(status().isForbidden());

        verify(diaryGenerationService, never()).generateForDate(any(), any());
    }

    @Test
    void 未ログインの生成はログイン画面へ() throws Exception {
        mockMvc.perform(post("/diary/2026-10-07/generate").with(csrf())).andExpect(redirectedUrl("/login"));

        verify(diaryGenerationService, never()).generateForDate(any(), any());
    }
}
