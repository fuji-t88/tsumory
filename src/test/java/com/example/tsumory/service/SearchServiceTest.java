package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.service.HighlightedText.Segment;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    private PostRepository postRepository;

    @Mock
    private DiaryRepository diaryRepository;

    @InjectMocks
    private SearchService service;

    private final User user = new User("demo", "hash", Instant.EPOCH);

    private static Instant jst(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(PostService.ZONE).toInstant();
    }

    private Post post(String content, String postedAt) {
        return new Post(user, content, jst(postedAt));
    }

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    @Test
    void escapeLike_prefixesMetaCharactersWithBang() {
        assertThat(SearchService.escapeLike("Curry")).isEqualTo("Curry");
        // ユーザーが % や _ を入力しても、全件や任意の 1 文字に一致させない
        assertThat(SearchService.escapeLike("100%")).isEqualTo("100!%");
        assertThat(SearchService.escapeLike("a_b")).isEqualTo("a!_b");
        // エスケープ文字の ! 自身も、先にエスケープする（二重にならない）
        assertThat(SearchService.escapeLike("やった!%")).isEqualTo("やった!!!%");
        // ESCAPE '!' にしたので、\ は普通の文字
        assertThat(SearchService.escapeLike("C:\tmp")).isEqualTo("C:\tmp");
    }

    @Test
    void search_keywordOnly_searchesPostsAndDiariesAndMergesNewestFirst() {
        when(postRepository.searchByKeyword(USER_ID, "カレー", LIMIT))
                .thenReturn(List.of(post("夜もカレー", "2026-10-07T20:00:00"), post("昼のカレー", "2026-10-06T12:40:00")));
        when(diaryRepository.searchByContent(USER_ID, "カレー", FIRST_PAGE))
                .thenReturn(
                        new PageImpl<>(List.of(new Diary(user, LocalDate.of(2026, 10, 6), "カレーの日。", Instant.EPOCH))));

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
    void search_passesEscapedKeyword() {
        when(postRepository.searchByKeyword(USER_ID, "100!%", LIMIT)).thenReturn(List.of());
        when(diaryRepository.searchByContent(USER_ID, "100!%", FIRST_PAGE)).thenReturn(Page.empty());

        service.search(USER_ID, "100%", null);

        verify(diaryRepository).searchByContent(USER_ID, "100!%", FIRST_PAGE);
    }

    @Test
    void search_postHitHasJstTimeHighlightAndCategory() {
        var hit = post("昼のカレーが辛すぎた", "2026-10-07T00:30:00");
        hit.assignCategory(PostCategory.PRIVATE);
        when(postRepository.searchByKeyword(USER_ID, "カレー", LIMIT)).thenReturn(List.of(hit));
        when(diaryRepository.searchByContent(USER_ID, "カレー", FIRST_PAGE)).thenReturn(Page.empty());

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
        when(postRepository.searchByKeywordAndCategory(USER_ID, "カレー", PostCategory.WORK, LIMIT))
                .thenReturn(List.of());

        service.search(USER_ID, "カレー", PostCategory.WORK);

        verify(diaryRepository, never()).searchByContent(anyLong(), anyString(), any());
    }

    @Test
    void search_categoryOnly_searchesPostsByCategory() {
        when(postRepository.searchByCategory(USER_ID, PostCategory.LEARNING, LIMIT))
                .thenReturn(List.of(post("資格の勉強", "2026-10-07T20:00:00")));

        var result = service.search(USER_ID, "", PostCategory.LEARNING);

        assertThat(result.hits()).hasSize(1);
        verify(postRepository, never()).searchByKeyword(anyLong(), anyString(), any());
        verify(diaryRepository, never()).searchByContent(anyLong(), anyString(), any());
    }

    @Test
    void search_moreThan50_returns50AndMarksTruncated() {
        var posts = IntStream.range(0, 51)
                .mapToObj(i -> post("カレー" + i, "2026-10-07T12:00:00"))
                .toList();
        when(postRepository.searchByKeyword(eq(USER_ID), anyString(), eq(LIMIT)))
                .thenReturn(posts);
        // 日記は 51 件目以降が次のページにある
        var diaries = IntStream.range(0, 50)
                .mapToObj(i -> new Diary(user, LocalDate.of(2026, 1, 1).plusDays(i), "カレー", Instant.EPOCH))
                .toList();
        when(diaryRepository.searchByContent(USER_ID, "カレー", FIRST_PAGE))
                .thenReturn(new PageImpl<>(diaries, FIRST_PAGE, 51));

        var result = service.search(USER_ID, "カレー", null);

        assertThat(result.hits()).hasSize(100);
        assertThat(result.postsTruncated()).isTrue();
        assertThat(result.diariesTruncated()).isTrue();
    }

    @Test
    void recentDiaries_usesPageNumberOnlyWithFixedSize() {
        var diary = new Diary(user, LocalDate.of(2026, 10, 7), "寝坊した一日。", Instant.EPOCH);
        when(diaryRepository.findByUserIdOrderByDiaryDateDesc(USER_ID, PageRequest.of(2, 20)))
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
    void excerpt_keepsWholeTextWhenShortAndReplacesLineBreaks() {
        assertThat(SearchService.excerpt("朝は寝坊。\n昼はカレー。", "カレー")).isEqualTo("朝は寝坊。 昼はカレー。");
    }
}
