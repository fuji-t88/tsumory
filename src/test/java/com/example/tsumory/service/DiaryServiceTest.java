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
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final List<PostItem> POSTS = List.of(new PostItem(10L, "寝坊した", LocalTime.of(8, 12), false));

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostService postService;

    @Mock
    private DiaryAiService diaryAiService;

    private final User user = new User("demo", "hash", Instant.EPOCH);

    private DiaryService service;

    @BeforeEach
    void setUp() {
        service = new DiaryService(
                diaryRepository,
                userRepository,
                postService,
                diaryAiService,
                TransactionOperations.withoutTransaction(),
                Clock.fixed(NOW, PostService.ZONE));
        lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        lenient().when(postService.isEditableDate(DATE)).thenReturn(true);
        lenient().when(diaryRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void findDiaryForSummary_doesNotRecheckDate() {
        // 呼び出し側が「今日」を決めたあとに 0 時をまたぎ、昨日が一昨日の扱いになった場合でも 404 にしない
        var yesterday = DATE.minusDays(1);
        var diary = new Diary(user, yesterday, "昨日の日記", Instant.parse("2026-10-07T13:30:00Z"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, yesterday)).thenReturn(Optional.of(diary));

        var result = service.findDiaryForSummary(USER_ID, yesterday);

        assertThat(result).contains(new DiaryItem(yesterday, "昨日の日記", LocalTime.of(22, 30)));
        verify(postService, never()).isEditableDate(any());
    }

    @Test
    void generate_dateNotAvailable_throwsNotFound() {
        var old = DATE.minusDays(2);
        when(postService.isEditableDate(old)).thenReturn(false);

        assertThatThrownBy(() -> service.generate(USER_ID, old)).isInstanceOf(DiaryNotFoundException.class);
        verify(diaryAiService, never()).write(any(), any());
    }

    @Test
    void generate_noPosts_doesNotCallAi() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(List.of());

        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.NoPosts.class);
        verify(diaryAiService, never()).write(any(), any());
    }

    @Test
    void generate_firstTime_insertsDiary() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryAiService.write(DATE, POSTS)).thenReturn(new DiaryAiResult.Written("寝坊した一日。"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.empty());

        var result = service.generate(USER_ID, DATE);

        var captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("寝坊した一日。");
        assertThat(captor.getValue().getGeneratedAt()).isEqualTo(NOW);
        assertThat(result).isEqualTo(new DiaryGenerationResult.Generated(captor.getValue()));
    }

    @Test
    void generate_again_rewritesExistingDiary() {
        var existing = new Diary(user, DATE, "前の日記", Instant.parse("2026-10-08T03:00:00Z"));
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryAiService.write(DATE, POSTS)).thenReturn(new DiaryAiResult.Written("新しい日記"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.of(existing));

        var result = service.generate(USER_ID, DATE);

        assertThat(result).isEqualTo(new DiaryGenerationResult.Generated(existing));
        assertThat(existing.getContent()).isEqualTo("新しい日記");
        assertThat(existing.getGeneratedAt()).isEqualTo(NOW);
        verify(diaryRepository, never()).saveAndFlush(any());
    }

    @Test
    void generate_uniqueViolationOnInsert_rewritesRowInsertedFirst() {
        var insertedFirst = new Diary(user, DATE, "先に入った日記", Instant.parse("2026-10-08T11:59:00Z"));
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryAiService.write(DATE, POSTS)).thenReturn(new DiaryAiResult.Written("新しい日記"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(insertedFirst));
        when(diaryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        var result = service.generate(USER_ID, DATE);

        assertThat(result).isEqualTo(new DiaryGenerationResult.Generated(insertedFirst));
        assertThat(insertedFirst.getContent()).isEqualTo("新しい日記");
        assertThat(insertedFirst.getGeneratedAt()).isEqualTo(NOW);
        verify(diaryRepository, times(1)).saveAndFlush(any());
        verify(diaryAiService, times(1)).write(any(), any());
    }

    @Test
    void generate_failed_keepsPreviousDiaryAndCallsAiOnce() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryAiService.write(DATE, POSTS)).thenReturn(new DiaryAiResult.Failed());

        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.Failed.class);
        verify(diaryAiService, times(1)).write(any(), any());
        verify(diaryRepository, never()).findByUserIdAndDiaryDate(any(), any());
        verify(diaryRepository, never()).saveAndFlush(any());
    }

    @Test
    void generate_refused() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryAiService.write(DATE, POSTS)).thenReturn(new DiaryAiResult.Refused());

        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.Refused.class);
        verify(diaryRepository, never()).saveAndFlush(any());
    }

    @Test
    void generate_concurrentSameUserAndDate_secondIsInProgress() throws Exception {
        var aiStarted = new CountDownLatch(1);
        var releaseAi = new CountDownLatch(1);
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryAiService.write(DATE, POSTS)).thenAnswer(invocation -> {
            aiStarted.countDown();
            releaseAi.await(5, TimeUnit.SECONDS);
            return new DiaryAiResult.Failed();
        });

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.generate(USER_ID, DATE));
            assertThat(aiStarted.await(5, TimeUnit.SECONDS)).isTrue();

            var second = service.generate(USER_ID, DATE);
            releaseAi.countDown();

            assertThat(second).isInstanceOf(DiaryGenerationResult.InProgress.class);
            assertThat(first.get(5, TimeUnit.SECONDS)).isInstanceOf(DiaryGenerationResult.Failed.class);
        }
        verify(diaryAiService, times(1)).write(any(), any());
    }

    @Test
    void generate_afterFailureOrException_canGenerateAgain() {
        when(postService.findPosts(USER_ID, DATE)).thenReturn(POSTS);
        when(diaryAiService.write(DATE, POSTS))
                .thenReturn(new DiaryAiResult.Failed())
                .thenThrow(new IllegalStateException("unexpected"))
                .thenReturn(new DiaryAiResult.Written("日記"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.empty());

        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.Failed.class);
        assertThatThrownBy(() -> service.generate(USER_ID, DATE)).isInstanceOf(IllegalStateException.class);
        assertThat(service.generate(USER_ID, DATE)).isInstanceOf(DiaryGenerationResult.Generated.class);
    }
}
