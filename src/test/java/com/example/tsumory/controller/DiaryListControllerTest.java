package com.example.tsumory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.config.CustomUserDetailsService;
import com.example.tsumory.config.SecurityConfig;
import com.example.tsumory.repository.UserRepository;
import com.example.tsumory.service.DiaryCalendarDay;
import com.example.tsumory.service.DiaryCalendarWeek;
import com.example.tsumory.service.DiaryDaySummary;
import com.example.tsumory.service.DiaryListService;
import com.example.tsumory.service.DiaryMonthNotFoundException;
import com.example.tsumory.service.DiaryMonthSummary;
import com.example.tsumory.service.PostService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiaryListController.class)
@Import({SecurityConfig.class, CustomUserDetailsService.class, DiaryListControllerTest.FixedClock.class})
class DiaryListControllerTest {

    private static final Long USER_ID = 1L;
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final CustomUserDetails PRINCIPAL = new CustomUserDetails(USER_ID, "demo", "$2a$10$hash");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            // 2026-10-15 21:00（日本時間）
            return Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), PostService.ZONE);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryListService diaryListService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        when(diaryListService.currentMonth()).thenReturn(OCTOBER);
        when(diaryListService.summarize(USER_ID, OCTOBER)).thenReturn(october());
        when(diaryListService.summarize(eq(USER_ID), eq(YearMonth.of(2026, 11))))
                .thenThrow(new DiaryMonthNotFoundException(YearMonth.of(2026, 11)));
    }

    private static DiaryMonthSummary october() {
        var days = List.of(
                new DiaryDaySummary(LocalDate.of(2026, 10, 12), false, null, 1),
                new DiaryDaySummary(LocalDate.of(2026, 10, 7), true, "寝坊した一日。", 2));
        // 10/1 は木曜なので 4 マス空ける
        var cells = new ArrayList<DiaryCalendarDay>();
        for (var i = 0; i < 4; i++) {
            cells.add(new DiaryCalendarDay(null, false, false, false, false));
        }
        for (var day = 1; day <= 31; day++) {
            var date = OCTOBER.atDay(day);
            cells.add(new DiaryCalendarDay(date, day == 7, day == 7 || day == 12, day <= 15, day == 15));
        }
        var weeks = new ArrayList<DiaryCalendarWeek>();
        for (var i = 0; i < cells.size(); i += 7) {
            weeks.add(new DiaryCalendarWeek(cells.subList(i, i + 7)));
        }
        return new DiaryMonthSummary(OCTOBER, days, weeks, 1, YearMonth.of(2026, 9), null);
    }

    @Test
    void 未ログインならログイン画面へリダイレクト() throws Exception {
        mockMvc.perform(get("/diaries")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 月の指定がなければ今月をリストで出す() throws Exception {
        var html = mockMvc.perform(get("/diaries").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(view().name("diaries"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("2026年10月")
                .contains("この月は 1 本の日記が積もっています")
                // 新しい日付から順。日記のない日は破線で「日記はまだありません」
                .containsSubsequence("10月12日（月）", "10月7日（水）")
                .contains("diary-day-empty")
                .contains("日記はまだありません")
                .contains("寝坊した一日。")
                .contains("つぶやき 2")
                .contains("href=\"/diary/2026-10-07\"")
                .doesNotContain("<table");
    }

    @Test
    void ヘッダーのメニューは日記一覧を選択中にする() throws Exception {
        var html = mockMvc.perform(get("/diaries").with(user(PRINCIPAL)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .containsPattern("href=\"/diaries\" class=\"nav-link active\"\\s+aria-current=\"page\"")
                .contains("href=\"/\" class=\"nav-link\">今日</a>")
                .contains("href=\"/search\" class=\"nav-link\">検索</a>");
    }

    @Test
    void カレンダー表示は日記ありとつぶやきのみの印と今日の枠を出す() throws Exception {
        var html = mockMvc.perform(get("/diaries?month=2026-10&view=calendar").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("<table")
                .contains("aria-label=\"日記あり\"")
                .contains("aria-label=\"つぶやきのみ\"")
                .contains("calendar-today")
                // 開ける日はリンク、未来の日はリンクにしない
                .contains("href=\"/diary/2026-10-15\"")
                .doesNotContain("href=\"/diary/2026-10-16\"");
    }

    @Test
    void 前の月へはリンクし今月より後へはリンクしない() throws Exception {
        var html = mockMvc.perform(get("/diaries?month=2026-10").with(user(PRINCIPAL)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("href=\"/diaries?month=2026-09&amp;view=list\"")
                .doesNotContain("month=2026-11");
    }

    @Test
    void 不明なviewはリスト表示にする() throws Exception {
        mockMvc.perform(get("/diaries?view=grid").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    void 範囲外の月と形式が不正な月は404() throws Exception {
        mockMvc.perform(get("/diaries?month=2026-11").with(user(PRINCIPAL))).andExpect(status().isNotFound());
        mockMvc.perform(get("/diaries?month=2026-1").with(user(PRINCIPAL))).andExpect(status().isNotFound());
        mockMvc.perform(get("/diaries?month=abc").with(user(PRINCIPAL))).andExpect(status().isNotFound());
    }

    @Test
    void 日記もつぶやきもない月はその旨を出す() throws Exception {
        when(diaryListService.summarize(any(), eq(YearMonth.of(2026, 9))))
                .thenReturn(new DiaryMonthSummary(YearMonth.of(2026, 9), List.of(), List.of(), 0, null, OCTOBER));

        mockMvc.perform(get("/diaries?month=2026-09").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("この月の日記はまだありません")))
                .andExpect(content().string(containsString("この月の日記とつぶやきはありません")));
    }
}
