package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long POST_ID = 10L;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    private final User user = new User("demo", "hash", Instant.EPOCH);

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        lenient().when(postRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PostService serviceAt(String jstDateTime) {
        var clock = Clock.fixed(jst(jstDateTime), PostService.ZONE);
        return new PostService(postRepository, userRepository, clock);
    }

    private static Instant jst(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(PostService.ZONE).toInstant();
    }

    @Test
    void today_isJstDate() {
        // UTC ではまだ 10/7 だが、日本時間では 10/8
        assertThat(serviceAt("2026-10-08T00:00:00").today()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(serviceAt("2026-10-08T23:59:59").today()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void isEditableDate_onlyTodayAndYesterday() {
        var service = serviceAt("2026-10-08T00:00:00");

        assertThat(service.isEditableDate(LocalDate.of(2026, 10, 8))).isTrue();
        assertThat(service.isEditableDate(LocalDate.of(2026, 10, 7))).isTrue();
        assertThat(service.isEditableDate(LocalDate.of(2026, 10, 6))).isFalse();
        assertThat(service.isEditableDate(LocalDate.of(2026, 10, 9))).isFalse();
    }

    @Test
    void findPosts_searchesFromJstMidnightToNextMidnight() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "おはよう", jst("2026-10-08T07:30:00"));
        when(postRepository.findInRange(USER_ID, jst("2026-10-08T00:00:00"), jst("2026-10-09T00:00:00")))
                .thenReturn(List.of(post));

        var items = service.findPosts(USER_ID, LocalDate.of(2026, 10, 8));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.content()).isEqualTo("おはよう");
            assertThat(item.postedTime()).isEqualTo(LocalTime.of(7, 30));
            assertThat(item.edited()).isFalse();
        });
    }

    @Test
    void post_savesStrippedContentWithSamePostedAndUpdatedTime() {
        var service = serviceAt("2026-10-08T12:00:00");

        service.post(USER_ID, "  ランチ\n");

        var captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getContent()).isEqualTo("ランチ");
        assertThat(saved.getPostedAt()).isEqualTo(jst("2026-10-08T12:00:00"));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getPostedAt());
        assertThat(saved.isEdited()).isFalse();
    }

    @Test
    void edit_keepsPostedAtAndMarksEdited() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.edit(USER_ID, POST_ID, " カレー ");

        assertThat(post.getContent()).isEqualTo("カレー");
        assertThat(post.getPostedAt()).isEqualTo(jst("2026-10-08T11:00:00"));
        assertThat(post.getUpdatedAt()).isEqualTo(jst("2026-10-08T12:00:00"));
        assertThat(post.isEdited()).isTrue();
    }

    @Test
    void edit_yesterdaysPostJustAfterMidnight() {
        var service = serviceAt("2026-10-08T00:00:30");
        var post = new Post(user, "おやすみ", jst("2026-10-07T23:59:59"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.edit(USER_ID, POST_ID, "おやすみなさい");

        assertThat(post.getContent()).isEqualTo("おやすみなさい");
    }

    @Test
    void edit_postFromDayBeforeYesterday_throwsNotFound() {
        var service = serviceAt("2026-10-08T00:00:30");
        var post = new Post(user, "おやすみ", jst("2026-10-06T23:59:59"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, "直したい")).isInstanceOf(PostNotFoundException.class);
        assertThat(post.getContent()).isEqualTo("おやすみ");
    }

    @Test
    void delete_othersPost_throwsNotFound() {
        var service = serviceAt("2026-10-08T12:00:00");
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, POST_ID)).isInstanceOf(PostNotFoundException.class);
        verify(postRepository, never()).delete(any());
    }

    @Test
    void delete_ownPost() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.delete(USER_ID, POST_ID);

        verify(postRepository).delete(post);
    }
}
