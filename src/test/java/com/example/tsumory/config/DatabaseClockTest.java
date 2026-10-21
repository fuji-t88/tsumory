package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DatabaseClockTest {

    private static final Instant T = Instant.parse("2026-05-26T14:59:58Z");
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final MutableClock system = new MutableClock(T, JST);

    @Test
    void システム時刻にDBの時刻との差を足して返す() {
        var clock = new DatabaseClock(() -> T.plusSeconds(5), system);

        assertThat(clock.instant()).isEqualTo(T.plusSeconds(5));

        system.advance(Duration.ofSeconds(10));
        assertThat(clock.instant()).isEqualTo(T.plusSeconds(15));
    }

    @Test
    void システム時刻が進んでいればDBの時刻まで戻す() {
        var clock = new DatabaseClock(() -> T.minusSeconds(30), system);

        assertThat(clock.instant()).isEqualTo(T.minusSeconds(30));
    }

    @Test
    void 問い合わせの往復の時間の半分を見込む() {
        // 問い合わせに 2 秒かかり、DB はその真ん中（1 秒後）に T+31 秒と答えた。差は 30 秒
        var clock = new DatabaseClock(
                () -> {
                    system.advance(Duration.ofSeconds(2));
                    return T.plusSeconds(31);
                },
                system);

        assertThat(clock.instant()).isEqualTo(T.plusSeconds(2).plusSeconds(30));
    }

    @Test
    void 日付の変わり目でもインスタンスのシステム時刻によらずDBの時刻の日付になる() {
        // システム時刻は 5 月 26 日 23:59:58（JST）、DB は 5 月 27 日 00:00:03（JST）
        var clock = new DatabaseClock(() -> T.plusSeconds(5), system);

        assertThat(LocalDate.now(clock)).hasToString("2026-05-27");
    }

    @Test
    void 測り直すと新しい差を使う() {
        var answer = new Answer(T.plusSeconds(5));
        var clock = new DatabaseClock(answer, system);

        answer.set(T.plusSeconds(20));
        clock.sync();

        assertThat(clock.instant()).isEqualTo(T.plusSeconds(20));
    }

    @Test
    void 測り直しに失敗したら前の差を使い続ける() {
        var answer = new Answer(T.plusSeconds(5));
        var clock = new DatabaseClock(answer, system);

        answer.fail();
        clock.sync();

        assertThat(clock.instant()).isEqualTo(T.plusSeconds(5));
    }

    @Test
    void 最初に測れなければ作れない() {
        var answer = new Answer(T);
        answer.fail();

        assertThatThrownBy(() -> new DatabaseClock(answer, system))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void タイムゾーンはシステム時刻の時計のものを使う() {
        var clock = new DatabaseClock(() -> T, system);

        assertThat(clock.getZone()).isEqualTo(JST);
    }

    @Test
    void タイムゾーンを変えても同じ差を使い測り直しも反映される() {
        var answer = new Answer(T.plusSeconds(5));
        var clock = new DatabaseClock(answer, system);
        var utc = clock.withZone(ZoneOffset.UTC);

        answer.set(T.plusSeconds(20));
        clock.sync();

        assertThat(utc.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(utc.instant()).isEqualTo(T.plusSeconds(20));
    }

    /** 進め方をテストで決められるシステム時刻 */
    private static final class MutableClock extends Clock {

        private Instant now;
        private final ZoneId zone;

        MutableClock(Instant now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(now, zone);
        }
    }

    /** DB の答え。途中で変えたり、失敗させたりできる */
    private static final class Answer implements Supplier<Instant> {

        private Instant time;
        private boolean failing;

        Answer(Instant time) {
            this.time = time;
        }

        void set(Instant time) {
            this.time = time;
        }

        void fail() {
            failing = true;
        }

        @Override
        public Instant get() {
            if (failing) {
                throw new DataAccessResourceFailureException("connection refused");
            }
            return time;
        }
    }
}
