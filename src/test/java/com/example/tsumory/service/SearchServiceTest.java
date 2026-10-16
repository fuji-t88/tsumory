package com.example.tsumory.service;

import static com.example.tsumory.domain.TestFixtures.NOW;
import static com.example.tsumory.domain.TestFixtures.STUDY;
import static com.example.tsumory.domain.TestFixtures.clockAt;
import static com.example.tsumory.domain.TestFixtures.diaries;
import static com.example.tsumory.domain.TestFixtures.diary;
import static com.example.tsumory.domain.TestFixtures.hanako;
import static com.example.tsumory.domain.TestFixtures.post;
import static com.example.tsumory.domain.TestFixtures.posts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.User;
import com.example.tsumory.service.HighlightedText.Segment;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    private static final Long USER_ID = 1L;
    private static final Limit LIMIT = Limit.of(51);

    @Mock
    private PostService postService;

    @Mock
    private DiaryService diaryService;

    private SearchService service;

    private final User user = hanako();

    @BeforeEach
    void setUp() {
        // 日付・時刻は日本時間で求める（Clock のタイムゾーン）
        service = new SearchService(postService, diaryService, clockAt(NOW));
    }

    /** 花子が {@code postedAt}（日本時間）に投稿したつぶやき。 */
    private Post postAt(String content, String postedAt) {
        return post(user, content, postedAt);
    }

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    @Test
    void search_withoutKeywordAndCategory_throwsInsteadOfReturningNothing() {
        assertThatThrownBy(() -> service.search(USER_ID, "", null)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(postService, diaryService);
    }

    @Test
    void search_keywordOnly_searchesPostsAndDiariesAndMergesNewestFirst() {
        when(postService.searchPosts(USER_ID, "カレー", null, LIMIT))
                .thenReturn(
                        List.of(postAt("夜もカレーにした", "2026-10-07T20:00:00"), postAt("昼は社食のカレー", "2026-10-06T12:40:00")));
        when(diaryService.searchDiaries(USER_ID, "カレー", FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(
                        diary(user, LocalDate.of(2026, 10, 6), "昼も夜もカレーだったことに、寝る前に気づいた。", "2026-10-06T22:00:00"))));

        var result = service.search(USER_ID, "カレー", null);

        // 日記は日記の日付の 23:59:59 として比べるので、10/7 20:00 のつぶやきと 10/6 12:40 のつぶやきの間に入る
        assertThat(result.hits())
                .extracting(SearchHit::date, SearchHit::isDiary)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 7), false),
                        tuple(LocalDate.of(2026, 10, 6), true),
                        tuple(LocalDate.of(2026, 10, 6), false));
        assertThat(result.postsTruncated()).isFalse();
        assertThat(result.diariesTruncated()).isFalse();
    }

    @Test
    void search_passesUnescapedKeywordToBothServices() {
        when(postService.searchPosts(USER_ID, "100%", null, LIMIT)).thenReturn(List.of());
        when(diaryService.searchDiaries(USER_ID, "100%", FIRST_PAGE)).thenReturn(Page.empty());

        service.search(USER_ID, "100%", null);

        // LIKE 用のエスケープは、つぶやき・日記それぞれのサービスが行う（ここでエスケープすると二重になる）
        verify(postService).searchPosts(USER_ID, "100%", null, LIMIT);
        verify(diaryService).searchDiaries(USER_ID, "100%", FIRST_PAGE);
    }

    @Test
    void search_postHitHasJstTimeHighlightAndCategory() {
        var hit = postAt("昼のカレーが辛すぎた", "2026-10-07T00:30:00");
        hit.assignCategory(PostCategory.PRIVATE);
        when(postService.searchPosts(USER_ID, "カレー", null, LIMIT)).thenReturn(List.of(hit));
        when(diaryService.searchDiaries(USER_ID, "カレー", FIRST_PAGE)).thenReturn(Page.empty());

        var result =
                (SearchHit.PostHit) service.search(USER_ID, "カレー", null).hits().getFirst();

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(result.time()).isEqualTo(LocalTime.of(0, 30));
        assertThat(result.category()).isEqualTo(PostCategory.PRIVATE);
        assertThat(result.content().segments())
                .containsExactly(new Segment("昼の", false), new Segment("カレー", true), new Segment("が辛すぎた", false));
    }

    @Test
    void search_withCategory_doesNotSearchDiaries() {
        when(postService.searchPosts(USER_ID, "カレー", PostCategory.WORK, LIMIT)).thenReturn(List.of());

        service.search(USER_ID, "カレー", PostCategory.WORK);

        verify(diaryService, never()).searchDiaries(anyLong(), any(), any());
    }

    @Test
    void search_categoryOnly_searchesPostsByCategory() {
        when(postService.searchPosts(USER_ID, "", PostCategory.LEARNING, LIMIT))
                .thenReturn(List.of(post(user, STUDY, "2026-10-07T20:00:00", PostCategory.LEARNING)));

        var result = service.search(USER_ID, "", PostCategory.LEARNING);

        assertThat(result.hits()).hasSize(1);
        verify(diaryService, never()).searchDiaries(anyLong(), any(), any());
    }

    @Test
    void search_moreThan50_returns50AndMarksTruncated() {
        // 検索の中身（どの本文が一致するか）はリポジトリのモックで決めるので、ここでは件数だけを見る
        // つぶやきは上限より 1 件多い 51 件
        when(postService.searchPosts(USER_ID, "会議", null, LIMIT)).thenReturn(posts(user, 51, "2026-10-07T09:00:00"));
        // 日記は 1 ページ目の 50 件。51 件目以降が次のページにある
        when(diaryService.searchDiaries(USER_ID, "会議", FIRST_PAGE))
                .thenReturn(new PageImpl<>(diaries(user, 50, LocalDate.of(2026, 8, 1)), FIRST_PAGE, 51));

        var result = service.search(USER_ID, "会議", null);

        assertThat(result.hits()).hasSize(100);
        assertThat(result.postsTruncated()).isTrue();
        assertThat(result.diariesTruncated()).isTrue();
    }

    @Test
    void recentDiaries_usesPageNumberOnlyWithFixedSize() {
        var diary = diary(user, LocalDate.of(2026, 10, 7), "寝坊した一日。", "2026-10-07T22:00:00");
        when(diaryService.searchDiaries(USER_ID, null, PageRequest.of(2, 20)))
                .thenReturn(new PageImpl<>(List.of(diary), PageRequest.of(2, 20), 41));

        // リクエストで size や sort を指定されても、件数と並び順は変えない
        var page = service.recentDiaries(USER_ID, PageRequest.of(2, 2000, Sort.by("content")));

        assertThat(page.getContent()).singleElement().satisfies(hit -> {
            assertThat(hit.isDiary()).isTrue();
            assertThat(((SearchHit.DiaryHit) hit).excerpt().segments()).containsExactly(new Segment("寝坊した一日。", false));
        });
    }

    @Test
    void excerpt_cutsAroundFirstMatch() {
        var content = "あ".repeat(40) + "カレー" + "い".repeat(70);

        assertThat(SearchService.excerpt(content, "カレー"))
                .isEqualTo("…" + "あ".repeat(30) + "カレー" + "い".repeat(60) + "…");
    }

    @Test
    void excerpt_countsEmojiAsOneCharacter() {
        // 絵文字（サロゲートペア）も 1 文字として数える。前 30 文字・後ろ 60 文字は、絵文字でも同じ文字数になる
        var content = "😀".repeat(40) + "カレー" + "🍛".repeat(70);

        assertThat(SearchService.excerpt(content, "カレー"))
                .isEqualTo("…" + "😀".repeat(30) + "カレー" + "🍛".repeat(60) + "…");
    }

    @Test
    void excerpt_keepsWholeTextWhenShortAndReplacesLineBreaks() {
        assertThat(SearchService.excerpt("朝は寝坊。\n昼はカレー。", "カレー")).isEqualTo("朝は寝坊。 昼はカレー。");
    }
}
