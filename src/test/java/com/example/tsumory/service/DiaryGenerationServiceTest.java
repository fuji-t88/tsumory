package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DiaryGenerationServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);
    private static final List<PostItem> POSTS = List.of(new PostItem(10L, "寝坊した", LocalTime.of(8, 12), false));
    private static final List<String> CONTENTS = List.of("寝坊した");

    @Mock
    private PostService postService;

    @Mock
    private DiaryService diaryService;

    @Mock
    private DiaryWriter diaryWriter;

    private final Diary diary = new Diary(new User("demo", "hash", Instant.EPOCH), DATE, "日記", Instant.EPOCH);

    private DiaryGenerationService service;

    @BeforeEach
    void setUp() {
        service = new DiaryGenerationService(postService, diaryService, diaryWriter);
        lenient().when(postService.isEditableDate(DATE)).thenReturn(true);
    }

    @Test
    void generate_dateNotAvailable_throwsNotFound() {
        var old = DATE.minusDays(2);
        when(postService.isEditableDate(old)).thenReturn(false);

        assertThatThrownBy(() -> service.generate(USER_ID, old)).isInstanceOf(DiaryNotFoundException.class);
        verify(diaryWriter, never()).write(any(), any());
    }

    @Test
    void generate_noPosts_doesNotCallWriter() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(List.of());

        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.NoPosts.class);
        verify(diaryWriter, never()).write(any(), any());
    }

    @Test
    void generate_written_passesContentsInOrderAndSavesDiary() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryWriter.write(DATE, CONTENTS)).thenReturn("寝坊した一日。");
        when(diaryService.save(USER_ID, DATE, "寝坊した一日。")).thenReturn(diary);

        assertThat(service.generate(USER_ID, DATE)).isEqualTo(new DiaryGenerationResult.Generated(diary));
    }

    @Test
    void generate_uniqueViolationOnInsert_savesAgainInNewTransaction() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryWriter.write(DATE, CONTENTS)).thenReturn("新しい日記");
        when(diaryService.save(USER_ID, DATE, "新しい日記"))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(diary);

        assertThat(service.generate(USER_ID, DATE)).isEqualTo(new DiaryGenerationResult.Generated(diary));
        verify(diaryService, times(2)).save(USER_ID, DATE, "新しい日記");
        verify(diaryWriter, times(1)).write(any(), any());
    }

    @Test
    void generate_failed_keepsPreviousDiaryAndCallsWriterOnce() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryWriter.write(DATE, CONTENTS)).thenThrow(new DiaryWriteFailedException());

        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.Failed.class);
        verify(diaryWriter, times(1)).write(any(), any());
        verify(diaryService, never()).save(any(), any(), any());
    }

    @Test
    void generate_refused_doesNotSave() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryWriter.write(DATE, CONTENTS)).thenThrow(new DiaryRefusedException());

        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.Refused.class);
        verify(diaryService, never()).save(any(), any(), any());
    }

    @Test
    void generate_concurrentSameUserAndDate_secondIsInProgress() throws Exception {
        var writerStarted = new CountDownLatch(1);
        var releaseWriter = new CountDownLatch(1);
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryWriter.write(DATE, CONTENTS)).thenAnswer(invocation -> {
            writerStarted.countDown();
            releaseWriter.await(5, TimeUnit.SECONDS);
            throw new DiaryWriteFailedException();
        });

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.generate(USER_ID, DATE));
            assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();

            var second = service.generate(USER_ID, DATE);
            releaseWriter.countDown();

            assertThat(second).isInstanceOf(DiaryGenerationResult.InProgress.class);
            assertThat(first.get(5, TimeUnit.SECONDS)).isInstanceOf(DiaryGenerationResult.Failed.class);
        }
        verify(diaryWriter, times(1)).write(any(), any());
    }

    @Test
    void generate_afterFailureOrException_canGenerateAgain() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryWriter.write(DATE, CONTENTS))
                .thenThrow(new DiaryWriteFailedException())
                .thenThrow(new IllegalStateException("unexpected"))
                .thenReturn("日記");
        when(diaryService.save(USER_ID, DATE, "日記")).thenReturn(diary);

        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.Failed.class);
        assertThatThrownBy(() -> service.generate(USER_ID, DATE)).isInstanceOf(IllegalStateException.class);
        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.Generated.class);
    }
}
