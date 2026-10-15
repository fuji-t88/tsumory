package com.example.tsumory.service;

import static com.example.tsumory.domain.TestData.DIARY_CONTENT;
import static com.example.tsumory.domain.TestData.NOW;
import static com.example.tsumory.domain.TestData.TODAY;
import static com.example.tsumory.domain.TestData.clockAt;
import static com.example.tsumory.domain.TestData.diary;
import static com.example.tsumory.domain.TestData.hanako;
import static com.example.tsumory.domain.TestData.jst;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    private static final Long USER_ID = 1L;

    /** 花子の登録日（日本時間）。TestData の登録日時（2026-10-01 0:00）の日付。 */
    private static final LocalDate REGISTERED_DATE = LocalDate.of(2026, 10, 1);

    /** 先週の日付。登録日より後で、一昨日より前。 */
    private static final LocalDate LAST_WEEK = LocalDate.of(2026, 10, 2);

    /** 前に書いた日記の本文。上書きのテストで「前の版」として使う。 */
    private static final String OLD_DIARY_CONTENT = "朝から雨で、一日じゅう家で本を読んで過ごした。";

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private UserRepository userRepository;

    private final User user = hanako();

    // 今は TestData の基準（2026-10-08 12:00、日本時間）
    private DiaryService service;

    @BeforeEach
    void setUp() {
        service = serviceAt(NOW);
        lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(diaryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private DiaryService serviceAt(String jstDateTime) {
        return new DiaryService(diaryRepository, userRepository, clockAt(jstDateTime));
    }

    private void stubNoDiary(LocalDate date) {
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, date)).thenReturn(Optional.empty());
    }

    private Diary capturedSavedDiary() {
        var captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository).save(captor.capture());
        return captor.getValue();
    }

    // --- 登録日と開ける日付 ---

    @Test
    void registeredDate_isJstDate() {
        // 登録は日本時間の 10/1 0:00 ちょうど（UTC ではまだ 9/30）
        assertThat(service.registeredDate(USER_ID)).isEqualTo(REGISTERED_DATE);
    }

    @Test
    void registeredDate_unknownUser_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registeredDate(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void isAvailableDate_fromRegisteredDateToToday() {
        assertThat(service.isAvailableDate(USER_ID, REGISTERED_DATE)).isTrue();
        assertThat(service.isAvailableDate(USER_ID, TODAY)).isTrue();
        assertThat(service.isAvailableDate(USER_ID, REGISTERED_DATE.minusDays(1)))
                .isFalse();
        assertThat(service.isAvailableDate(USER_ID, TODAY.plusDays(1))).isFalse();
    }

    @Test
    void isAvailableDate_todaySwitchesAtJstMidnight() {
        // 日本時間の 10/9 0:00 ちょうど（UTC ではまだ 10/8 の 15:00）。この瞬間から 10/9 を開ける
        var tomorrow = TODAY.plusDays(1);

        assertThat(serviceAt("2026-10-09T00:00:00").isAvailableDate(USER_ID, tomorrow))
                .isTrue();
        assertThat(serviceAt("2026-10-08T23:59:59").isAvailableDate(USER_ID, tomorrow))
                .isFalse();
    }

    // --- 取得 ---

    @Test
    void findByDate_returnsDiaryOfAvailableDate() {
        // 一昨日より前でも、登録日以降なら開ける
        var diary = diary(user, LAST_WEEK, DIARY_CONTENT, "2026-10-02T21:30:00");
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, LAST_WEEK)).thenReturn(Optional.of(diary));

        assertThat(service.findByDate(USER_ID, LAST_WEEK)).containsSame(diary);
    }

    @Test
    void findByDate_availableDateWithoutDiary_returnsEmpty() {
        // 開ける日付で日記がまだないのは正常（画面で「この日の日記はまだありません」を出す）。404 にしない
        stubNoDiary(TODAY);

        assertThat(service.findByDate(USER_ID, TODAY)).isEmpty();
    }

    @Test
    void findByDate_dateNotAvailable_throwsNotFound() {
        for (var date : new LocalDate[] {REGISTERED_DATE.minusDays(1), TODAY.plusDays(1)}) {
            assertThatThrownBy(() -> service.findByDate(USER_ID, date)).isInstanceOf(DiaryNotFoundException.class);
        }
        verify(diaryRepository, never()).findByUserIdAndDiaryDate(any(), any());
    }

    @Test
    void findDiaryForSummary_doesNotRecheckDate() {
        // 呼び出し側が「今日」を決めたあとに 0 時をまたぎ、昨日が一昨日の扱いになった場合でも 404 にしない
        var yesterday = TODAY.minusDays(1);
        var diary = diary(user, yesterday, DIARY_CONTENT, "2026-10-07T22:30:00");
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, yesterday)).thenReturn(Optional.of(diary));

        var result = service.findDiaryForSummary(USER_ID, yesterday);

        assertThat(result).contains(new DiaryItem(yesterday, DIARY_CONTENT, LocalTime.of(22, 30)));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void findDiaryForSummary_noDiary_returnsEmpty() {
        stubNoDiary(TODAY);

        assertThat(service.findDiaryForSummary(USER_ID, TODAY)).isEmpty();
    }

    // --- 一覧と検索 ---
    // searchDiaries はキーワードの有無で委譲先を切り替える。分岐の数（2 つ）だけ、委譲先と、その結果がそのまま返ることを確かめる。
    // 戻り値は、Spring Data の Page を返すメソッドでよく使う PageImpl と PageRequest で組み立てる

    @Test
    void searchDiaries_blankKeyword_returnsAllNewestFirst() {
        // given
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(diary(user, TODAY, DIARY_CONTENT, "2026-10-08T21:30:00")), pageable, 1);
        when(diaryRepository.findByUserIdOrderByDiaryDateDesc(USER_ID, pageable))
                .thenReturn(page);

        // when & then：null・空文字・空白だけは、どれも絞り込みのない一覧
        assertThat(service.searchDiaries(USER_ID, null, pageable)).isSameAs(page);
        assertThat(service.searchDiaries(USER_ID, "", pageable)).isSameAs(page);
        assertThat(service.searchDiaries(USER_ID, " 　", pageable)).isSameAs(page);
        verify(diaryRepository, never()).searchByContent(any(), any(), any());
    }

    @Test
    void searchDiaries_withKeyword_searchesByContent() {
        // given
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(diary(user, TODAY, DIARY_CONTENT, "2026-10-08T21:30:00")), pageable, 1);
        when(diaryRepository.searchByContent(USER_ID, "塩バターパン", pageable)).thenReturn(page);

        // when
        var result = service.searchDiaries(USER_ID, "塩バターパン", pageable);

        // then：検索用のメソッドに委譲し、その結果をそのまま返す。一覧用のメソッドは呼ばない
        assertThat(result).isSameAs(page);
        assertThat(result.getContent()).extracting(Diary::getContent).containsExactly(DIARY_CONTENT);
        verify(diaryRepository, never()).findByUserIdOrderByDiaryDateDesc(any(), any());
    }

    @Test
    void searchDiaries_escapesBangFirstThenWildcards() {
        var pageable = PageRequest.of(0, 50);
        when(diaryRepository.searchByContent(any(), any(), any())).thenReturn(Page.empty());

        service.searchDiaries(USER_ID, "満足度100%", pageable);
        service.searchDiaries(USER_ID, "snake_case", pageable);
        // ! を先に二重化するので、後から付けた ! は再エスケープされない（"!%!!" になり "!!%!!" にはならない）
        service.searchDiaries(USER_ID, "達成率100%!", pageable);

        verify(diaryRepository).searchByContent(USER_ID, "満足度100!%", pageable);
        verify(diaryRepository).searchByContent(USER_ID, "snake!_case", pageable);
        verify(diaryRepository).searchByContent(USER_ID, "達成率100!%!!", pageable);
    }

    // --- 保存（新規と上書き） ---

    @Test
    void saveDiary_createsNewDiary() {
        // given
        var diaryService = serviceAt(NOW);
        stubNoDiary(TODAY);
        // JPA の save() は保存したエンティティを返すので、渡された日記をそのまま返す
        when(diaryRepository.save(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Diary result = diaryService.saveDiary(USER_ID, TODAY, DIARY_CONTENT);

        // then
        assertThat(result.getContent()).isEqualTo(DIARY_CONTENT);
        assertThat(result.getDiaryDate()).isEqualTo(TODAY);
        verify(diaryRepository).save(any(Diary.class));
    }

    @Test
    void saveDiary_firstTime_insertsTodaysDiary() {
        stubNoDiary(TODAY);

        var saved = service.saveDiary(USER_ID, TODAY, DIARY_CONTENT);

        var captured = capturedSavedDiary();
        assertThat(captured.getDiaryDate()).isEqualTo(TODAY);
        assertThat(captured.getContent()).isEqualTo(DIARY_CONTENT);
        assertThat(captured.getGeneratedAt()).isEqualTo(jst(NOW));
        assertThat(saved).isSameAs(captured);
    }

    @Test
    void saveDiary_createsTheDiaryForTheLoggedInUser() {
        // 新しく作る日記は、ログイン中のユーザーのもの（ほかのユーザーの日記として保存しない）
        stubNoDiary(TODAY);

        var saved = service.saveDiary(USER_ID, TODAY, DIARY_CONTENT);

        assertThat(saved.getUser()).isSameAs(user);
        verify(userRepository).getReferenceById(USER_ID);
    }

    @Test
    void saveDiary_pastDate_insertsThatDaysDiaryWithNowAsGeneratedAt() {
        // 遡り生成：日記の日付はその日、生成時刻は書いた今
        stubNoDiary(LAST_WEEK);

        service.saveDiary(USER_ID, LAST_WEEK, OLD_DIARY_CONTENT);

        var captured = capturedSavedDiary();
        assertThat(captured.getDiaryDate()).isEqualTo(LAST_WEEK);
        assertThat(captured.getGeneratedAt()).isEqualTo(jst(NOW));
        verify(diaryRepository, never()).findByUserIdAndDiaryDate(USER_ID, TODAY);
    }

    @Test
    void saveDiary_updatesExistingDiary() {
        // given
        var diaryService = serviceAt(NOW);
        var existing = diary(user, TODAY, OLD_DIARY_CONTENT, "2026-10-08T11:00:00");
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, TODAY)).thenReturn(Optional.of(existing));

        // when
        Diary result = diaryService.saveDiary(USER_ID, TODAY, DIARY_CONTENT);

        // then
        assertThat(result.getContent()).isEqualTo(DIARY_CONTENT);
        // 既存の日記は updateContent で上書きし、新しく save しない（JPA が変更を検知して書き込む）
        verify(diaryRepository, never()).save(any(Diary.class));
    }

    @Test
    void saveDiary_again_rewritesTodaysDiary() {
        var existing = diary(user, TODAY, OLD_DIARY_CONTENT, "2026-10-08T03:00:00");
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, TODAY)).thenReturn(Optional.of(existing));

        var saved = service.saveDiary(USER_ID, TODAY, DIARY_CONTENT);

        assertThat(saved).isSameAs(existing);
        assertThat(existing.getContent()).isEqualTo(DIARY_CONTENT);
        assertThat(existing.getGeneratedAt()).isEqualTo(jst(NOW));
        verify(diaryRepository, never()).save(any());
    }
}
