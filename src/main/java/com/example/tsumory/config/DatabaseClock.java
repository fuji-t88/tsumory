package com.example.tsumory.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * DB の時刻に合わせた時計。DB の時刻を起点にし、そこからの経過時間を単調に増える {@code nanoTime} で足して返す。
 *
 * <p>理由は phase1-spec.md の「5. 処理の詳細 > 現在時刻の基準（本番は DB の時刻）」。
 */
@Slf4j
public class DatabaseClock extends Clock {

    static final String SYNC_INTERVAL = "PT1M";

    private final Supplier<Instant> databaseTime;
    private final LongSupplier nanoTime;
    private final ZoneId zone;
    private final AtomicReference<Sample> sample;

    /**
     * 作るときに 1 回、DB の時刻を測る。
     *
     * @param databaseTime DB の現在時刻を問い合わせる
     * @param nanoTime 経過時間を測る単調な時計（本番では {@code System::nanoTime}）
     * @param zone 返す時計のタイムゾーン
     * @throws RuntimeException 最初の問い合わせに失敗したとき（その例外をそのまま投げる）
     */
    public DatabaseClock(Supplier<Instant> databaseTime, LongSupplier nanoTime, ZoneId zone) {
        this(databaseTime, nanoTime, zone, new AtomicReference<>(Sample.take(databaseTime, nanoTime)));
        log.info("Clock source: database (offset from system clock={}ms)", offsetFromSystemClock());
    }

    private DatabaseClock(
            Supplier<Instant> databaseTime, LongSupplier nanoTime, ZoneId zone, AtomicReference<Sample> sample) {
        this.databaseTime = Objects.requireNonNull(databaseTime, "databaseTime");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.sample = sample;
    }

    /** DB の時刻を測り直す。測れなければ、前に測った時刻を起点に進め続ける。 */
    @Scheduled(fixedDelayString = SYNC_INTERVAL, initialDelayString = SYNC_INTERVAL)
    public void sync() {
        try {
            sample.set(Sample.take(databaseTime, nanoTime));
            log.debug("Database clock synced: offset from system clock={}ms", offsetFromSystemClock());
        } catch (RuntimeException e) {
            log.warn(
                    "Database clock sync failed, keeping the previous sample: {}",
                    e.getClass().getName());
        }
    }

    // ログで DB とインスタンスの時計のずれを見るためだけに、システム時刻と比べる
    private long offsetFromSystemClock() {
        return Duration.between(Clock.systemUTC().instant(), instant()).toMillis();
    }

    @Override
    public Instant instant() {
        return sample.get().instantAt(nanoTime.getAsLong());
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    // 同じ測った値を共有する（測り直しは元の時計だけが行い、こちらにも反映される）
    @Override
    public Clock withZone(ZoneId zone) {
        return new DatabaseClock(databaseTime, nanoTime, zone, sample);
    }

    /** 測った DB の時刻と、そのときの {@code nanoTime} */
    private record Sample(Instant databaseTime, long sampledAtNanos) {

        // 問い合わせの往復の時間の半分を見込み、前後の nanoTime の真ん中を DB の時刻を測った時点とみなす
        static Sample take(Supplier<Instant> databaseTime, LongSupplier nanoTime) {
            var before = nanoTime.getAsLong();
            var database = databaseTime.get();
            var after = nanoTime.getAsLong();
            return new Sample(database, before + (after - before) / 2);
        }

        Instant instantAt(long nowNanos) {
            return databaseTime.plusNanos(nowNanos - sampledAtNanos);
        }
    }
}
