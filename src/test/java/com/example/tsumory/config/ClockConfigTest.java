package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class ClockConfigTest {

    private static final Duration DB_AHEAD = Duration.ofHours(3);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class)
            .withBean(JdbcTemplate.class, ClockConfigTest::jdbcTemplateAhead);

    @Test
    void 設定がなければ日本時間のシステム時刻を使う() {
        runner.run(context -> {
            var clock = context.getBean(Clock.class);

            assertThat(clock).isNotInstanceOf(DatabaseClock.class);
            assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
        });
    }

    @Test
    void databaseならDBの時刻に合わせた日本時間の時計を使い定期的に測り直す() {
        runner.withPropertyValues("app.clock.source=database").run(context -> {
            var clock = context.getBean(Clock.class);

            assertThat(clock).isInstanceOf(DatabaseClock.class);
            assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
            assertThat(Duration.between(Clock.systemUTC().instant(), clock.instant()))
                    .isBetween(DB_AHEAD.minusMinutes(1), DB_AHEAD.plusMinutes(1));
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class)
                            .getScheduledTasks())
                    .hasSize(1);
        });
    }

    @Test
    void 決まった値でなければ起動しない() {
        runner.withPropertyValues("app.clock.source=databse")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("app.clock.source must be system or database"));
    }

    // DB の時刻がシステム時刻より 3 時間進んでいる（ずれが測られていることを、テストの実行時刻によらず見分けるため）
    private static JdbcTemplate jdbcTemplateAhead() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT clock_timestamp()"), eq(OffsetDateTime.class)))
                .thenAnswer(invocation -> OffsetDateTime.now(Clock.systemUTC()).plus(DB_AHEAD));
        return jdbc;
    }
}
