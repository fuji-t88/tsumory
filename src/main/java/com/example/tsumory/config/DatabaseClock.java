package com.example.tsumory.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * DB の時刻に合わせた時計。DB の時刻とシステム時刻の差を定期的に測り、システム時刻にその差を足して返す。
 *
 * <p>理由は phase1-spec.md の「5. 処理の詳細 > 現在時刻の基準（本番は DB の時刻）」。
 */
@Slf4j
public class DatabaseClock extends Clock {

    static final String SYNC_INTERVAL = "PT1M";

    private final Supplier<Instant> databaseTime;
    private final Clock systemClock;
    private final AtomicReference<Duration> offset;

    /**
     * 作るときに 1 回、DB の時刻との差を測る。
     *
     * @param databaseTime DB の現在時刻を問い合わせる
     * @param systemClock システム時刻（タイムゾーンはこの時計のものを使う）
     * @throws RuntimeException 最初の問い合わせに失敗したとき（その例外をそのまま投げる）
     */
    public DatabaseClock(Supplier<Instant> databaseTime, Clock systemClock) {
        this(databaseTime, systemClock, new AtomicReference<>(measureOffset(databaseTime, systemClock)));
        log.info("Clock source: database (offset={}ms)", offset.get().toMillis());
    }

    private DatabaseClock(Supplier<Instant> databaseTime, Clock systemClock, AtomicReference<Duration> offset) {
        this.databaseTime = Objects.requireNonNull(databaseTime, "databaseTime");
        this.systemClock = Objects.requireNonNull(systemClock, "systemClock");
        this.offset = offset;
    }

    /** DB の時刻との差を測り直す。測れなければ、前に測った差を使い続ける。 */
    @Scheduled(fixedDelayString = SYNC_INTERVAL, initialDelayString = SYNC_INTERVAL)
    public void sync() {
        try {
            offset.set(measureOffset(databaseTime, systemClock));
            log.debug("Database clock synced: offset={}ms", offset.get().toMillis());
        } catch (RuntimeException e) {
            log.warn(
                    "Database clock sync failed, keeping offset={}ms: {}",
                    offset.get().toMillis(),
                    e.getClass().getName());
        }
    }

    // 問い合わせの往復の時間の半分を見込み、前後のシステム時刻の真ん中を DB の時刻と比べる
    private static Duration measureOffset(Supplier<Instant> databaseTime, Clock systemClock) {
        var before = systemClock.instant();
        var database = databaseTime.get();
        var after = systemClock.instant();
        var midpoint = before.plus(Duration.between(before, after).dividedBy(2));
        return Duration.between(midpoint, database);
    }

    @Override
    public Instant instant() {
        return systemClock.instant().plus(offset.get());
    }

    @Override
    public ZoneId getZone() {
        return systemClock.getZone();
    }

    // 同じ差を共有する（測り直しは元の時計だけが行い、こちらにも反映される）
    @Override
    public Clock withZone(ZoneId zone) {
        return new DatabaseClock(databaseTime, systemClock.withZone(zone), offset);
    }
}
