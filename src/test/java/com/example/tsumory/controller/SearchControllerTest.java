package com.example.tsumory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.config.CustomUserDetailsService;
import com.example.tsumory.config.SecurityConfig;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.TestFixtures;
import com.example.tsumory.repository.UserRepository;
import com.example.tsumory.service.HighlightedText;
import com.example.tsumory.service.HighlightedText.Segment;
import com.example.tsumory.service.SearchHit;
import com.example.tsumory.service.SearchResult;
import com.example.tsumory.service.SearchService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
@Import({SecurityConfig.class, CustomUserDetailsService.class, SearchControllerTest.FixedClock.class})
class SearchControllerTest {

    private static final Long USER_ID = 1L;
    private static final CustomUserDetails PRINCIPAL = new CustomUserDetails(USER_ID, "demo", "$2a$10$hash");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), TestFixtures.JST);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private UserRepository userRepository;

    private String html(String url) throws Exception {
        return mockMvc.perform(get(url).with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void 未ログインならログイン画面へリダイレクト() throws Exception {
        mockMvc.perform(get("/search?q=カレー")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 初めて開いたときは検索せずに最近の日記を新しい順に出す() throws Exception {
        var diaryHit = new SearchHit.DiaryHit(
                LocalDate.of(2026, 10, 7),
                new HighlightedText(List.of(new Segment("寝坊した一日。", false))),
                Instant.parse("2026-10-07T14:59:59Z"));
        when(searchService.recentDiaries(any(), any()))
                .thenReturn(new PageImpl<>(List.of(diaryHit), PageRequest.of(0, 20), 21));

        var html = html("/search");

        assertThat(html)
                .contains("name=\"q\"")
                .contains("type=\"search\"")
                .contains("maxlength=\"50\"")
                .contains("すべて")
                .containsPattern("href=\"/search\" class=\"nav-link active\"\\s+aria-current=\"page\"")
                .contains("最近の日記")
                .contains("10月7日（水）")
                .contains("寝坊した一日。")
                .contains("href=\"/diary/2026-10-07\"")
                // 2 ページ目へのリンク。1 ページ目なので前へのリンクはない
                .contains("href=\"/search?page=1\"")
                .doesNotContain("新しい日記</a>")
                .doesNotContain("見つかりませんでした");
        verify(searchService, never()).search(anyLong(), anyString(), any());
    }

    @Test
    void ページ番号だけを受け取り件数は固定してserviceに渡す() throws Exception {
        when(searchService.recentDiaries(any(), any())).thenReturn(Page.empty());

        // size を指定されても、件数は変えない
        var html = html("/search?page=2&size=1000");

        assertThat(html).contains("まだ日記はありません");
        verify(searchService).recentDiaries(USER_ID, PageRequest.of(2, SearchService.RECENT_PAGE_SIZE));
    }

    @Test
    void 負のページ番号は0として扱う() throws Exception {
        when(searchService.recentDiaries(any(), any())).thenReturn(Page.empty());

        html("/search?page=-3");

        verify(searchService).recentDiaries(USER_ID, PageRequest.of(0, SearchService.RECENT_PAGE_SIZE));
    }

    @Test
    void 数字でないページ番号は404() throws Exception {
        mockMvc.perform(get("/search?page=abc").with(user(PRINCIPAL))).andExpect(status().isNotFound());

        verify(searchService, never()).recentDiaries(any(), any());
    }

    @Test
    void キーワードの前後の空白を取り除きログイン中のユーザーで検索する() throws Exception {
        when(searchService.search(USER_ID, "カレー", null)).thenReturn(new SearchResult(List.of(), false, false));

        var html = mockMvc.perform(get("/search").param("q", " カレー ").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("「カレー」 0 件").contains("見つかりませんでした");
        verify(searchService).search(USER_ID, "カレー", null);
    }

    @Test
    void 結果を種類のラベルと強調付きで出す() throws Exception {
        var postHit = new SearchHit.PostHit(
                LocalDate.of(2026, 10, 7),
                LocalTime.of(12, 40),
                new HighlightedText(
                        List.of(new Segment("昼の", false), new Segment("カレー", true), new Segment("<b>", false))),
                PostCategory.PRIVATE,
                Instant.parse("2026-10-07T03:40:00Z"));
        var diaryHit = new SearchHit.DiaryHit(
                LocalDate.of(2025, 12, 31),
                new HighlightedText(List.of(new Segment("カレーの日", false))),
                Instant.parse("2025-12-31T14:59:59Z"));
        when(searchService.search(USER_ID, "カレー", null))
                .thenReturn(new SearchResult(List.of(postHit, diaryHit), true, false));

        var html = html("/search?q=カレー");

        assertThat(html)
                .contains("「カレー」 2 件")
                .contains(">つぶやき</span>")
                .contains("10月7日 12:40")
                .contains("<p class=\"mb-1 mt-1\">昼の<mark>カレー</mark>&lt;b&gt;</p>")
                .contains("badge bg-success")
                .contains(">日記</span>")
                // 今年でない日付は年を付ける
                .contains("2025年12月31日")
                .contains("href=\"/diary/2026-10-07\"")
                .contains("href=\"/diary/2025-12-31\"")
                .contains("新しい順に 50 件まで表示しています");
    }

    @Test
    void カテゴリだけでも検索できる() throws Exception {
        when(searchService.search(USER_ID, "", PostCategory.WORK))
                .thenReturn(new SearchResult(List.of(), false, false));

        var html = html("/search?category=WORK");

        assertThat(html).contains("仕事 0 件").contains("value=\"WORK\" checked=\"checked\"");
    }

    @Test
    void カテゴリの選択肢はそれぞれのidでラベルと結び付ける() throws Exception {
        when(searchService.search(USER_ID, "", PostCategory.WORK))
                .thenReturn(new SearchResult(List.of(), false, false));

        var html = html("/search?category=WORK");

        assertThat(html)
                .containsOnlyOnce("id=\"category-WORK\"")
                .containsPattern("<label [^>]*for=\"category-WORK\"[^>]*>仕事</label>")
                .doesNotContain("category-option");
    }

    @Test
    void キーワードとカテゴリの要約は中黒でつなぐ() throws Exception {
        when(searchService.search(USER_ID, "カレー", PostCategory.PRIVATE))
                .thenReturn(new SearchResult(List.of(), false, false));

        assertThat(html("/search?q=カレー&category=PRIVATE")).contains("「カレー」・プライベート 0 件");
    }

    @Test
    void 候補にないカテゴリはすべてとして扱う() throws Exception {
        when(searchService.search(USER_ID, "カレー", null)).thenReturn(new SearchResult(List.of(), false, false));

        html("/search?q=カレー&category=HOBBY");

        verify(searchService).search(USER_ID, "カレー", null);
    }

    @Test
    void 五十一文字のキーワードはエラーを出して検索しない() throws Exception {
        var html = html("/search?q=" + "あ".repeat(51));

        assertThat(html).contains("50 文字以内で入力してください");
        verify(searchService, never()).search(anyLong(), anyString(), any());
    }
}
