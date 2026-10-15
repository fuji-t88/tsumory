package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.PostRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiaryListServiceTest {

    private static final Long USER_ID = 1L;
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private DiaryService diaryService;

    private final User user = new User("demo", "hash", Instant.EPOCH);

    private DiaryListService service;

    @BeforeEach
    void setUp() {
        // 今日は 2026-10-15（日本時間）。登録日は 2026-09-10
        service = new DiaryListService(
                diaryRepository,
                postRepository,
                diaryService,
                Clock.fixed(jst("2026-10-15T12:00:00"), PostService.ZONE));
        lenient().when(diaryService.registeredDate(USER_ID)).thenReturn(LocalDate.of(2026, 9, 10));
        lenient()
                .when(diaryRepository.findByUserIdAndDiaryDateBetweenOrderByDiaryDateDesc(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(postRepository.findPostedAtInRange(any(), any(), any())).thenReturn(List.of());
    }

    private static Instant jst(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(PostService.ZONE).toInstant();
    }

    private Diary diary(LocalDate date, String content) {
        return new Diary(user, date, content, Instant.EPOCH);
    }

    @Test
    void summarize_listsDaysWithDiaryOrPostsNewestFirst() {
        when(diaryRepository.findByUserIdAndDiaryDateBetweenOrderByDiaryDateDesc(
                        USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(diary(LocalDate.of(2026, 10, 7), "寝坊した一日。")));
        when(postRepository.findPostedAtInRange(USER_ID, jst("2026-10-01T00:00:00"), jst("2026-11-01T00:00:00")))
                .thenReturn(
                        List.of(jst("2026-10-07T08:00:00"), jst("2026-10-07T12:40:00"), jst("2026-10-12T21:00:00")));

        var summary = service.summarize(USER_ID, OCTOBER);

        assertThat(summary.days())
                .containsExactly(
                        new DiaryDaySummary(LocalDate.of(2026, 10, 12), false, null, 1),
                        new DiaryDaySummary(LocalDate.of(2026, 10, 7), true, "寝坊した一日。", 2));
        assertThat(summary.diaryCount()).isEqualTo(1);
    }

    @Test
    void summarize_countsPostsByJstDate() {
        // 23:59 は 10/7、0:00 は 10/8 に数える（UTC ではどちらも 10/7）
        when(postRepository.findPostedAtInRange(any(), any(), any()))
                .thenReturn(List.of(jst("2026-10-07T23:59:00"), jst("2026-10-08T00:00:00")));

        var summary = service.summarize(USER_ID, OCTOBER);

        assertThat(summary.days())
                .extracting(DiaryDaySummary::date, DiaryDaySummary::postCount)
                .containsExactly(tuple(LocalDate.of(2026, 10, 8), 1L), tuple(LocalDate.of(2026, 10, 7), 1L));
    }

    @Test
    void excerpt_replacesLineBreaksAndCutsAt60Characters() {
        assertThat(DiaryListService.excerpt("朝は寝坊した。\n昼はカレー。")).isEqualTo("朝は寝坊した。 昼はカレー。");
        assertThat(DiaryListService.excerpt("あ".repeat(60))).isEqualTo("あ".repeat(60));
        assertThat(DiaryListService.excerpt("あ".repeat(61))).isEqualTo("あ".repeat(60) + "…");
        // 絵文字（サロゲートペア）は 1 文字と数え、途中で切らない
        assertThat(DiaryListService.excerpt("😀".repeat(61))).isEqualTo("😀".repeat(60) + "…");
    }

    @Test
    void summarize_calendarStartsOnSundayWithBlanks() {
        // 2026-10-01 は木曜。日〜水の 4 マスを空ける
        var weeks = service.summarize(USER_ID, OCTOBER).weeks();

        assertThat(weeks).allSatisfy(week -> assertThat(week.days()).hasSize(7));
        var firstWeek = weeks.getFirst().days();
        assertThat(firstWeek.subList(0, 4)).allMatch(DiaryCalendarDay::isBlank);
        assertThat(firstWeek.get(4).date()).isEqualTo(LocalDate.of(2026, 10, 1));
        // 31 日（土）で終わるので、最後の週に空きマスはない
        assertThat(weeks.getLast().days().getLast().date()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void summarize_calendarMarksAvailableDaysAndToday() {
        var cells = service.summarize(USER_ID, OCTOBER).weeks().stream()
                .flatMap(week -> week.days().stream())
                .filter(cell -> !cell.isBlank())
                .toList();

        var today = cells.stream()
                .filter(cell -> cell.date().equals(LocalDate.of(2026, 10, 15)))
                .findFirst()
                .orElseThrow();
        var tomorrow = cells.stream()
                .filter(cell -> cell.date().equals(LocalDate.of(2026, 10, 16)))
                .findFirst()
                .orElseThrow();
        assertThat(today.today()).isTrue();
        assertThat(today.available()).isTrue();
        assertThat(tomorrow.available()).isFalse();
    }

    @Test
    void summarize_firstAndCurrentMonthHaveNoFurtherLinks() {
        var september = service.summarize(USER_ID, YearMonth.of(2026, 9));
        assertThat(september.previousMonth()).isNull();
        assertThat(september.nextMonth()).isEqualTo(OCTOBER);

        var october = service.summarize(USER_ID, OCTOBER);
        assertThat(october.previousMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(october.nextMonth()).isNull();
    }

    @Test
    void summarize_monthOutOfRange_throwsNotFound() {
        assertThatThrownBy(() -> service.summarize(USER_ID, YearMonth.of(2026, 8)))
                .isInstanceOf(DiaryMonthNotFoundException.class);
        assertThatThrownBy(() -> service.summarize(USER_ID, YearMonth.of(2026, 11)))
                .isInstanceOf(DiaryMonthNotFoundException.class);
    }
}
