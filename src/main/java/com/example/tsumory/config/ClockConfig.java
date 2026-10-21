package com.example.tsumory.config;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 現在時刻の基準を設定 {@code app.clock.source} で切り替える（phase1-spec.md の「5. 処理の詳細 > 現在時刻の基準（本番は DB の時刻）」）。 */
@Configuration
@EnableScheduling
public class ClockConfig {

    static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    /** @throws IllegalStateException {@code app.clock.source} が {@code system}・{@code database} のどちらでもないとき */
    @Bean
    public Clock clock(@Value("${app.clock.source:system}") String source, ObjectProvider<JdbcTemplate> jdbcTemplate) {
        return switch (source) {
            case "system" -> Clock.system(ZONE);
            case "database" -> {
                var jdbc = jdbcTemplate.getObject();
                yield new DatabaseClock(
                        () -> jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class)
                                .toInstant(),
                        Clock.system(ZONE));
            }
            default -> throw new IllegalStateException("app.clock.source must be system or database");
        };
    }
}
