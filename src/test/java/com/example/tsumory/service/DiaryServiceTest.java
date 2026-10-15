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
import java.time.ZoneId;
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
    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private UserRepository userRepository;

    // 登録は 2026-10-01 00:00（日本時間）ちょうど。UTC ではまだ 9/30
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-30T15:00:00Z");

    private final User user = new User("demo", "hash", REGISTERED_AT);

    private DiaryService service;

    @BeforeEach
    void setUp() {
        service = new DiaryService(diaryRepository, userRepository, Clock.fixed(NOW, PostService.ZONE));
        lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(diaryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registeredDate_isJstDate() {
        assertThat(service.registeredDate(USER_ID)).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void registeredDate_unknownUser_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registeredDate(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void isAvailableDate_fromRegisteredDateToToday() {
        assertThat(service.isAvailableDate(USER_ID, LocalDate.of(2026, 10, 1))).isTrue();
        assertThat(service.isAvailableDate(USER_ID, DATE)).isTrue();
        assertThat(service.isAvailableDate(USER_ID, LocalDate.of(2026, 9, 30))).isFalse();
        assertThat(service.isAvailableDate(USER_ID, DATE.plusDays(1))).isFalse();
    }

    @Test
    void isAvailableDate_todaySwitchesAtJstMidnight() {
        // 日本時間の 10/9 0:00 ちょうど（UTC ではまだ 10/8 の 15:00）。この瞬間から 10/9 を開ける
        var atMidnight = new DiaryService(
                diaryRepository, userRepository, Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), PostService.ZONE));
        var justBefore = new DiaryService(
                diaryRepository, userRepository, Clock.fixed(Instant.parse("2026-10-08T14:59:59Z"), PostService.ZONE));

        assertThat(atMidnight.isAvailableDate(USER_ID, LocalDate.of(2026, 10, 9)))
                .isTrue();
        assertThat(justBefore.isAvailableDate(USER_ID, LocalDate.of(2026, 10, 9)))
                .isFalse();
    }

    @Test
    void findByDate_returnsDiaryOfAvailableDate() {
        // 一昨日より前でも、登録日以降なら開ける
        var old = LocalDate.of(2026, 10, 2);
        var diary = new Diary(user, old, "日記", Instant.parse("2026-10-02T12:30:00Z"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, old)).thenReturn(Optional.of(diary));

        assertThat(service.findByDate(USER_ID, old)).containsSame(diary);
    }

    @Test
    void findByDate_dateNotAvailable_throwsNotFound() {
        for (var date : new LocalDate[] {LocalDate.of(2026, 9, 30), DATE.plusDays(1)}) {
            assertThatThrownBy(() -> service.findByDate(USER_ID, date)).isInstanceOf(DiaryNotFoundException.class);
        }
        verify(diaryRepository, never()).findByUserIdAndDiaryDate(any(), any());
    }

    // searchDiaries はキーワードの有無で委譲先を切り替える。分岐の数（2 つ）だけ、委譲先と、その結果がそのまま返ることを確かめる。
    // 戻り値は、Spring Data の Page を返すメソッドでよく使う PageImpl と PageRequest で組み立てる

    @Test
    void searchDiaries_blankKeyword_returnsAllNewestFirst() {
        // given
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(new Diary(user, DATE, "日記", NOW)), pageable, 1);
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
        var page = new PageImpl<>(List.of(new Diary(user, DATE, "カレーの日", NOW)), pageable, 1);
        when(diaryRepository.searchByContent(USER_ID, "カレー", pageable)).thenReturn(page);

        // when
        var result = service.searchDiaries(USER_ID, "カレー", pageable);

        // then：検索用のメソッドに委譲し、その結果をそのまま返す。一覧用のメソッドは呼ばない
        assertThat(result).isSameAs(page);
        assertThat(result.getContent()).extracting(Diary::getContent).containsExactly("カレーの日");
        verify(diaryRepository, never()).findByUserIdOrderByDiaryDateDesc(any(), any());
    }

    @Test
    void searchDiaries_escapesBangFirstThenWildcards() {
        var pageable = PageRequest.of(0, 50);
        when(diaryRepository.searchByContent(any(), any(), any())).thenReturn(Page.empty());

        service.searchDiaries(USER_ID, "100%", pageable);
        service.searchDiaries(USER_ID, "a_b", pageable);
        // ! を先に二重化するので、後から付けた ! は再エスケープされない（"!!!%" になり "!!!!%" にはならない）
        service.searchDiaries(USER_ID, "やった!%", pageable);

        verify(diaryRepository).searchByContent(USER_ID, "100!%", pageable);
        verify(diaryRepository).searchByContent(USER_ID, "a!_b", pageable);
        verify(diaryRepository).searchByContent(USER_ID, "やった!!!%", pageable);
    }

    @Test
    void findDiaryForSummary_doesNotRecheckDate() {
        // 呼び出し側が「今日」を決めたあとに 0 時をまたぎ、昨日が一昨日の扱いになった場合でも 404 にしない
        var yesterday = DATE.minusDays(1);
        var diary = new Diary(user, yesterday, "昨日の日記", Instant.parse("2026-10-07T13:30:00Z"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, yesterday)).thenReturn(Optional.of(diary));

        var result = service.findDiaryForSummary(USER_ID, yesterday);

        assertThat(result).contains(new DiaryItem(yesterday, "昨日の日記", LocalTime.of(22, 30)));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void findDiaryForSummary_noDiary_returnsEmpty() {
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.empty());

        assertThat(service.findDiaryForSummary(USER_ID, DATE)).isEmpty();
    }

    @Test
    void findByDate_availableDateWithoutDiary_returnsEmpty() {
        // 開ける日付で日記がまだないのは正常（画面で「この日の日記はまだありません」を出す）。404 にしない
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.empty());

        assertThat(service.findByDate(USER_ID, DATE)).isEmpty();
    }

    @Test
    void saveDiary_createsTheDiaryForTheLoggedInUser() {
        // 新しく作る日記は、ログイン中のユーザーのもの（ほかのユーザーの日記として保存しない）
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.empty());

        var saved = service.saveDiary(USER_ID, DATE, "寝坊した一日。");

        assertThat(saved.getUser()).isSameAs(user);
        verify(userRepository).getReferenceById(USER_ID);
    }

    @Test
    void saveDiary_firstTime_insertsTodaysDiary() {
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.empty());

        var saved = service.saveDiary(USER_ID, DATE, "寝坊した一日。");

        var captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository).save(captor.capture());
        assertThat(captor.getValue().getDiaryDate()).isEqualTo(DATE);
        assertThat(captor.getValue().getContent()).isEqualTo("寝坊した一日。");
        assertThat(captor.getValue().getGeneratedAt()).isEqualTo(NOW);
        assertThat(saved).isSameAs(captor.getValue());
    }

    @Test
    void saveDiary_pastDate_insertsThatDaysDiaryWithNowAsGeneratedAt() {
        // 遡り生成：日記の日付はその日、生成時刻は書いた今
        var past = LocalDate.of(2026, 10, 2);
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, past)).thenReturn(Optional.empty());

        service.saveDiary(USER_ID, past, "先週の日記");

        var captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository).save(captor.capture());
        assertThat(captor.getValue().getDiaryDate()).isEqualTo(past);
        assertThat(captor.getValue().getGeneratedAt()).isEqualTo(NOW);
        verify(diaryRepository, never()).findByUserIdAndDiaryDate(USER_ID, DATE);
    }

    @Test
    void saveDiary_createsNewDiary() {
        // given
        var clock = Clock.fixed(Instant.parse("2026-05-23T01:00:00Z"), ZoneId.of("Asia/Tokyo"));
        var diaryService = new DiaryService(diaryRepository, userRepository, clock);
        var userId = 1L;
        var content = "今日の日記です。";
        var today = LocalDate.now(clock);
        var user = new User("testuser", "encoded", Instant.now(clock));
        when(diaryRepository.findByUserIdAndDiaryDate(userId, today)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        // JPA の save() は保存したエンティティを返すので、渡された日記をそのまま返す
        when(diaryRepository.save(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Diary result = diaryService.saveDiary(userId, today, content);

        // then
        assertThat(result.getContent()).isEqualTo(content);
        assertThat(result.getDiaryDate()).isEqualTo(today);
        verify(diaryRepository).save(any(Diary.class));
    }

    @Test
    void saveDiary_updatesExistingDiary() {
        // given
        var clock = Clock.fixed(Instant.parse("2026-05-23T01:00:00Z"), ZoneId.of("Asia/Tokyo"));
        var diaryService = new DiaryService(diaryRepository, userRepository, clock);
        var userId = 1L;
        var today = LocalDate.now(clock);
        var user = new User("testuser", "encoded", Instant.now(clock));
        var existing = new Diary(user, today, "古い日記", Instant.now(clock).minusSeconds(3600));
        when(diaryRepository.findByUserIdAndDiaryDate(userId, today)).thenReturn(Optional.of(existing));

        // when
        var newContent = "新しい日記です。";
        Diary result = diaryService.saveDiary(userId, today, newContent);

        // then
        assertThat(result.getContent()).isEqualTo(newContent);
        // 既存の日記は updateContent で上書きし、新しく save しない（JPA が変更を検知して書き込む）
        verify(diaryRepository, never()).save(any(Diary.class));
    }

    @Test
    void saveDiary_again_rewritesTodaysDiary() {
        var existing = new Diary(user, DATE, "前の日記", Instant.parse("2026-10-08T03:00:00Z"));
        when(diaryRepository.findByUserIdAndDiaryDate(USER_ID, DATE)).thenReturn(Optional.of(existing));

        var saved = service.saveDiary(USER_ID, DATE, "新しい日記");

        assertThat(saved).isSameAs(existing);
        assertThat(existing.getContent()).isEqualTo("新しい日記");
        assertThat(existing.getGeneratedAt()).isEqualTo(NOW);
        verify(diaryRepository, never()).save(any());
    }
}
