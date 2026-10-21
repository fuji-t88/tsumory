package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DatabaseClockTest {

    private static final Instant T = Instant.parse("2026-05-26T14:59:58Z");
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final Nanos nanos = new Nanos();

    @Test
    void DBの時刻を起点に経過時間を足して返す() {
        var clock = new DatabaseClock(() -> T.plusSeconds(5), nanos, JST);

        assertThat(clock.instant()).isEqualTo(T.plusSeconds(5));

        nanos.advance(Duration.ofSeconds(10));
        assertThat(clock.instant()).isEqualTo(T.plusSeconds(15));
    }

    @Test
    void 経過時間は単調な時計だけで測りOSの時計には左右されない() {
        // OS の時計（壁時計）が NTP の補正などで飛んでも、nanoTime が進んだ分だけ進む
        var clock = new DatabaseClock(() -> T, nanos, JST);

        nanos.advance(Duration.ofMillis(1500));

        assertThat(clock.instant()).isEqualTo(T.plusMillis(1500));
    }

    @Test
    void 問い合わせの往復の時間の半分を見込む() {
        // 問い合わせに 2 秒かかり、DB はその真ん中（1 秒後）に T と答えた。答えを受け取った時点では T+1 秒
        var clock = new DatabaseClock(
                () -> {
                    nanos.advance(Duration.ofSeconds(2));
                    return T;
                },
                nanos,
                JST);

        assertThat(clock.instant()).isEqualTo(T.plusSeconds(1));
    }

    @Test
    void 日付の変わり目でもインスタンスのシステム時刻によらずDBの時刻の日付になる() {
        // インスタンスの時計は 5 月 26 日 23:59:58（JST）でも、DB が 5 月 27 日 00:00:03（JST）ならその日付
        var clock = new DatabaseClock(() -> T.plusSeconds(5), nanos, JST);

        assertThat(LocalDate.now(clock)).hasToString("2026-05-27");
    }

    @Test
    void 測り直すと新しいDBの時刻を起点にする() {
        var answer = new Answer(T);
        var clock = new DatabaseClock(answer, nanos, JST);

        nanos.advance(Duration.ofSeconds(60));
        answer.set(T.plusSeconds(75));
        clock.sync();

        assertThat(clock.instant()).isEqualTo(T.plusSeconds(75));
    }

    @Test
    void 測り直しに失敗したら前に測った時刻を起点に進め続ける() {
        var answer = new Answer(T);
        var clock = new DatabaseClock(answer, nanos, JST);

        nanos.advance(Duration.ofSeconds(60));
        answer.fail();
        clock.sync();

        assertThat(clock.instant()).isEqualTo(T.plusSeconds(60));
    }

    @Test
    void 最初に測れなければ作れない() {
        var answer = new Answer(T);
        answer.fail();

        assertThatThrownBy(() -> new DatabaseClock(answer, nanos, JST))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void 渡したタイムゾーンを使う() {
        var clock = new DatabaseClock(() -> T, nanos, JST);

        assertThat(clock.getZone()).isEqualTo(JST);
    }

    @Test
    void タイムゾーンを変えても同じ時刻を返し測り直しも反映される() {
        var answer = new Answer(T);
        var clock = new DatabaseClock(answer, nanos, JST);
        var utc = clock.withZone(ZoneOffset.UTC);

        answer.set(T.plusSeconds(20));
        clock.sync();

        assertThat(utc.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(utc.instant()).isEqualTo(T.plusSeconds(20));
    }

    /** 進め方をテストで決められる単調な時計（System.nanoTime の代わり） */
    private static final class Nanos implements LongSupplier {

        private long now = 123_456_789L;

        void advance(Duration duration) {
            now += duration.toNanos();
        }

        @Override
        public long getAsLong() {
            return now;
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
