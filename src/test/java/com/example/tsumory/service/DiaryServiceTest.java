package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostService postService;

    private final User user = new User("demo", "hash", Instant.EPOCH);

    private DiaryService service;

    @BeforeEach
    void setUp() {
        service = new DiaryService(diaryRepository, userRepository, postService, Clock.fixed(NOW, PostService.ZONE));
        lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        lenient().when(diaryRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void findByDate_returnsDiaryOfAvailableDate() {
        when(postService.isEditableDate(DATE)).thenReturn(true);
        var diary = new Diary(user, DATE, "日記", Instant.parse("2026-10-08T12:30:00Z"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.of(diary));

        assertThat(service.findByDate(USER_ID, DATE)).containsSame(diary);
    }

    @Test
    void findByDate_dateNotAvailable_throwsNotFound() {
        var old = DATE.minusDays(2);
        when(postService.isEditableDate(old)).thenReturn(false);

        assertThatThrownBy(() -> service.findByDate(USER_ID, old)).isInstanceOf(DiaryNotFoundException.class);
        verify(diaryRepository, never()).findByUserIdAndDiaryDate(any(), any());
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
    void saveDiary_firstTime_insertsTodaysDiary() {
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.empty());

        var saved = service.saveDiary(USER_ID, "寝坊した一日。");

        var captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("寝坊した一日。");
        assertThat(captor.getValue().getGeneratedAt()).isEqualTo(NOW);
        assertThat(saved).isSameAs(captor.getValue());
    }

    @Test
    void saveDiary_again_rewritesTodaysDiary() {
        var existing = new Diary(user, DATE, "前の日記", Instant.parse("2026-10-08T03:00:00Z"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.of(existing));

        var saved = service.saveDiary(USER_ID, "新しい日記");

        assertThat(saved).isSameAs(existing);
        assertThat(existing.getContent()).isEqualTo("新しい日記");
        assertThat(existing.getGeneratedAt()).isEqualTo(NOW);
        verify(diaryRepository, never()).saveAndFlush(any());
    }
}
