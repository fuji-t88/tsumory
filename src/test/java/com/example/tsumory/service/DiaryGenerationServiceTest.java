package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
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
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final LocalDate PAST = LocalDate.of(2026, 10, 1);

    @Mock
    private PostService postService;

    @Mock
    private DiaryService diaryService;

    @Mock
    private DiaryWriter diaryWriter;

    private DiaryGenerationService service;

    private final User user = new User("demo", "hash", Instant.EPOCH);

    @BeforeEach
    void setUp() {
        // 今日は 2026-10-08（日本時間）
        service =
                new DiaryGenerationService(postService, diaryService, diaryWriter, Clock.fixed(NOW, PostService.ZONE));
        lenient().when(diaryService.isAvailableDate(any(), any())).thenReturn(true);
    }

    @Test
    void generateForDate_pastDate_writesWithThatDaysPostsAndSavesAsThatDay() {
        // 一昨日より前の日も、その日のつぶやきからその日の日記として書く（今日の日記は変えない）
        var overslept = new Post(user, "寝坊した", NOW);
        var meeting = new Post(user, "会議が長引いた", NOW);
        meeting.assignCategory(PostCategory.WORK);
        var diary = new Diary(user, PAST, "寝坊した一日。", NOW);
        when(postService.findPostsByDate(USER_ID, PAST)).thenReturn(List.of(overslept, meeting));
        // 本文だけでなくカテゴリも使えるよう、つぶやきをそのまま投稿順で渡す
        when(diaryWriter.write(PAST, List.of(overslept, meeting))).thenReturn("寝坊した一日。");
        when(diaryService.saveDiary(USER_ID, PAST, "寝坊した一日。")).thenReturn(diary);

        assertThat(service.generateForDate(USER_ID, PAST)).isSameAs(diary);
    }

    @Test
    void generateForDate_today_savesAsToday() {
        when(postService.findPostsByDate(USER_ID, TODAY)).thenReturn(List.of(new Post(user, "寝坊した", NOW)));
        when(diaryWriter.write(any(), any())).thenReturn("寝坊した一日。");

        service.generateForDate(USER_ID, TODAY);

        verify(diaryService).saveDiary(USER_ID, TODAY, "寝坊した一日。");
    }

    @Test
    void generateForDate_futureDate_throwsInvalidDateWithoutCallingWriter() {
        // 今日は 10/8（日本時間）。10/9 は未来
        var future = TODAY.plusDays(1);

        assertThatThrownBy(() -> service.generateForDate(USER_ID, future))
                .isInstanceOf(InvalidDiaryDateException.class);
        verify(postService, never()).findPostsByDate(any(), any());
        verify(diaryWriter, never()).write(any(), any());
        verify(diaryService, never()).saveDiary(any(), any(), any());
    }

    @Test
    void generateForDate_beforeRegistration_throwsNotFoundWithoutCallingWriter() {
        var beforeRegistration = LocalDate.of(2026, 9, 1);
        when(diaryService.isAvailableDate(USER_ID, beforeRegistration)).thenReturn(false);

        assertThatThrownBy(() -> service.generateForDate(USER_ID, beforeRegistration))
                .isInstanceOf(DiaryNotFoundException.class);
        verify(diaryWriter, never()).write(any(), any());
        verify(diaryService, never()).saveDiary(any(), any(), any());
    }

    @Test
    void generateForDate_noPosts_throwsAndDoesNotCallWriter() {
        when(postService.findPostsByDate(USER_ID, PAST)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateForDate(USER_ID, PAST)).isInstanceOf(NoPostsException.class);
        verify(diaryWriter, never()).write(any(), any());
        verify(diaryService, never()).saveDiary(any(), any(), any());
    }

    @Test
    void generateForDate_writerFails_doesNotSaveAndRethrows() {
        when(postService.findPostsByDate(USER_ID, PAST)).thenReturn(List.of(new Post(user, "寝坊した", NOW)));
        when(diaryWriter.write(any(), any())).thenThrow(new DiaryGenerationException("failed"));

        assertThatThrownBy(() -> service.generateForDate(USER_ID, PAST)).isInstanceOf(DiaryGenerationException.class);
        verify(diaryService, never()).saveDiary(any(), any(), any());
    }

    @Test
    void generateForDate_doesNotHoldTransactionDuringApiCall() throws Exception {
        var method = DiaryGenerationService.class.getMethod("generateForDate", Long.class, LocalDate.class);

        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }
}
