package com.example.tsumory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

/**
 * 日付から投稿時刻の範囲への変換（default メソッド）の単体テスト。DB は使わず、クエリのメソッドはモックにする。
 *
 * <p>クエリそのもの（JPQL）が DB で正しく動くかは、Testcontainers を入れるまで手動で確かめる（docs/phase2-spec.md の「10. テスト」）。
 */
class PostRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    // default メソッドは本物を動かし、クエリのメソッドだけを差し替える
    private final PostRepository repository =
            mock(PostRepository.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));

    private static Instant jst(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(JST).toInstant();
    }

    @Test
    void findByUserIdAndDate_usesHalfOpenRangeFromMidnightToNextMidnight() {
        doReturn(List.of())
                .when(repository)
                .findInRange(USER_ID, jst("2026-10-08T00:00:00"), jst("2026-10-09T00:00:00"));

        repository.findByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 8), JST);

        // 終わりは翌日 0:00（その時刻は含まない）。23:59:59.999999999 を渡して DB で丸められ、翌日 0:00 を含むことがないように
        verify(repository).findInRange(USER_ID, jst("2026-10-08T00:00:00"), jst("2026-10-09T00:00:00"));
    }

    @Test
    void findByUserIdAndDate_dependsOnZone() {
        // 日本時間の 10/8 0:00 は、UTC ではまだ 10/7 の 15:00
        doReturn(List.of())
                .when(repository)
                .findInRange(USER_ID, Instant.parse("2026-10-07T15:00:00Z"), Instant.parse("2026-10-08T15:00:00Z"));

        repository.findByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 8), JST);

        verify(repository)
                .findInRange(USER_ID, Instant.parse("2026-10-07T15:00:00Z"), Instant.parse("2026-10-08T15:00:00Z"));
    }

    @Test
    void countByUserIdAndDate_usesTheSameRange() {
        doReturn(3L).when(repository).countInRange(USER_ID, jst("2026-10-08T00:00:00"), jst("2026-10-09T00:00:00"));

        assertThat(repository.countByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 8), JST))
                .isEqualTo(3L);
    }

    @Test
    void findPostedAtByUserIdAndMonth_coversTheWholeMonth() {
        doReturn(List.of())
                .when(repository)
                .findPostedAtInRange(USER_ID, jst("2026-10-01T00:00:00"), jst("2026-11-01T00:00:00"));

        repository.findPostedAtByUserIdAndMonth(USER_ID, YearMonth.of(2026, 10), JST);

        verify(repository).findPostedAtInRange(USER_ID, jst("2026-10-01T00:00:00"), jst("2026-11-01T00:00:00"));
    }
}
