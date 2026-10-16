package com.example.tsumory.service;

import static com.example.tsumory.domain.TestFixtures.DIARY_CONTENT;
import static com.example.tsumory.domain.TestFixtures.MEETING;
import static com.example.tsumory.domain.TestFixtures.NOW;
import static com.example.tsumory.domain.TestFixtures.OVERSLEPT;
import static com.example.tsumory.domain.TestFixtures.TODAY;
import static com.example.tsumory.domain.TestFixtures.clockAt;
import static com.example.tsumory.domain.TestFixtures.diary;
import static com.example.tsumory.domain.TestFixtures.hanako;
import static com.example.tsumory.domain.TestFixtures.mockPosts;
import static com.example.tsumory.domain.TestFixtures.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.User;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class DiaryGenerationServiceTest {

    private static final Long USER_ID = 1L;

    /** 先週の日付（登録日の 10/1）。遡り生成の対象にする。 */
    private static final LocalDate PAST = LocalDate.of(2026, 10, 1);

    @Mock
    private PostService postService;

    @Mock
    private DiaryService diaryService;

    @Mock
    private DiaryWriter diaryWriter;

    private DiaryGenerationService service;

    private final User user = hanako();

    @BeforeEach
    void setUp() {
        // 今日は TestFixtures の基準（2026-10-08、日本時間）
        service = new DiaryGenerationService(postService, diaryService, diaryWriter, clockAt(NOW));

        lenient().when(diaryService.isAvailableDate(any(), any())).thenReturn(true);
    }

    @Test
    void generateForDate_pastDate_writesWithThatDaysPostsAndSavesAsThatDay() {
        // 一昨日より前の日も、その日のつぶやきからその日の日記として書く（今日の日記は変えない）
        var overslept = post(user, OVERSLEPT, "2026-10-01T07:30:00");
        var meeting = post(user, MEETING, "2026-10-01T17:00:00", PostCategory.WORK);

        var diary = diary(user, PAST, DIARY_CONTENT, NOW);
        when(postService.findPostsByDate(USER_ID, PAST)).thenReturn(List.of(overslept, meeting));
        // 本文だけでなくカテゴリも使えるよう、つぶやきをそのまま投稿順で渡す
        when(diaryWriter.write(PAST, List.of(overslept, meeting))).thenReturn(DIARY_CONTENT);
        when(diaryService.saveDiary(USER_ID, PAST, DIARY_CONTENT)).thenReturn(diary);

        assertThat(service.generateForDate(USER_ID, PAST)).isSameAs(diary);
    }

    @Test
    void generateForDate_saveConflictsWithConcurrentGeneration_retriesAsUpdateWithoutCallingAiAgain() {
        // 別のタブから同じ日の日記が先に INSERT され、1 回目の保存が一意制約の違反になる
        var diary = diary(user, PAST, DIARY_CONTENT, NOW);
        when(postService.findPostsByDate(USER_ID, PAST)).thenReturn(mockPosts(3));
        when(diaryWriter.write(any(), any())).thenReturn(DIARY_CONTENT);
        when(diaryService.saveDiary(USER_ID, PAST, DIARY_CONTENT))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(diary);

        assertThat(service.generateForDate(USER_ID, PAST)).isSameAs(diary);

        verify(diaryWriter, times(1)).write(any(), any());
        verify(diaryService, times(2)).saveDiary(USER_ID, PAST, DIARY_CONTENT);
    }

    @Test
    void generateForDate_saveFailsAgainOnRetry_throws() {
        // 2 回目も失敗するなら一意制約以外の原因。隠さずに投げる（500）
        when(postService.findPostsByDate(USER_ID, PAST)).thenReturn(mockPosts(3));
        when(diaryWriter.write(any(), any())).thenReturn(DIARY_CONTENT);
        when(diaryService.saveDiary(USER_ID, PAST, DIARY_CONTENT))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.generateForDate(USER_ID, PAST))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(diaryService, times(2)).saveDiary(USER_ID, PAST, DIARY_CONTENT);
    }

    @Test
    void generateForDate_today_savesAsToday() {
        // 保存先の日付だけを確かめる。サービスはつぶやきが 0 件かどうかしか見ず、中身は日記を書くモックに渡すだけなので、モックで十分
        when(postService.findPostsByDate(USER_ID, TODAY)).thenReturn(mockPosts(5));
        when(diaryWriter.write(any(), any())).thenReturn(DIARY_CONTENT);

        service.generateForDate(USER_ID, TODAY);

        verify(diaryService).saveDiary(USER_ID, TODAY, DIARY_CONTENT);
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
        when(postService.findPostsByDate(USER_ID, PAST))
                .thenReturn(List.of(post(user, OVERSLEPT, "2026-10-08T07:30:00")));
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
