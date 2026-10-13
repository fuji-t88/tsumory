package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class DiaryGenerationServiceTest {

    private static final Long USER_ID = 1L;
    // 2026-10-08 21:00（日本時間）
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Mock
    private PostService postService;

    @Mock
    private DiaryService diaryService;

    @Mock
    private DiaryWriter diaryWriter;

    private final User user = new User("demo", "hash", Instant.EPOCH);
    private final Diary diary = new Diary(user, TODAY, "日記", NOW);

    private DiaryGenerationService service;

    @BeforeEach
    void setUp() {
        service =
                new DiaryGenerationService(postService, diaryService, diaryWriter, Clock.fixed(NOW, PostService.ZONE));
    }

    @Test
    void generateToday_noPosts_throwsAndDoesNotCallWriter() {
        when(postService.findTodayPosts(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateToday(USER_ID)).isInstanceOf(NoPostsException.class);
        verify(diaryWriter, never()).write(any(), any());
        verify(diaryService, never()).saveDiary(any(), any());
    }

    @Test
    void generateToday_passesTodayAndContentsInOrderAndSaves() {
        when(postService.findTodayPosts(USER_ID))
                .thenReturn(List.of(new Post(user, "寝坊した", NOW), new Post(user, "昼のカレーが辛すぎた", NOW)));
        when(diaryWriter.write(TODAY, List.of("寝坊した", "昼のカレーが辛すぎた"))).thenReturn("寝坊した一日。");
        when(diaryService.saveDiary(USER_ID, "寝坊した一日。")).thenReturn(diary);

        assertThat(service.generateToday(USER_ID)).isSameAs(diary);
    }

    @Test
    void generateToday_writerFails_doesNotSaveAndRethrows() {
        when(postService.findTodayPosts(USER_ID)).thenReturn(List.of(new Post(user, "寝坊した", NOW)));
        when(diaryWriter.write(any(), any())).thenThrow(new DiaryGenerationException("failed"));

        assertThatThrownBy(() -> service.generateToday(USER_ID)).isInstanceOf(DiaryGenerationException.class);
        verify(diaryService, never()).saveDiary(any(), any());
    }

    @Test
    void generateToday_writerRefuses_doesNotSaveAndRethrows() {
        when(postService.findTodayPosts(USER_ID)).thenReturn(List.of(new Post(user, "寝坊した", NOW)));
        when(diaryWriter.write(any(), any())).thenThrow(new DiaryGenerationException("refused"));

        assertThatThrownBy(() -> service.generateToday(USER_ID)).isInstanceOf(DiaryGenerationException.class);
        verify(diaryService, never()).saveDiary(any(), any());
    }

    @Test
    void generateToday_doesNotHoldTransactionDuringApiCall() throws Exception {
        var method = DiaryGenerationService.class.getMethod("generateToday", Long.class);

        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }
}
