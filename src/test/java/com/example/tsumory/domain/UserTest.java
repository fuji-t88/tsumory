package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-30T15:00:00Z");

    /** BCrypt のハッシュの形の値（{@code TestFixtures#hanako} と同じ）。 */
    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Test
    void 必須の値がnullなら作れない() {
        // 保存の時点の DB のエラー（500）ではなく、作る時点で気づける
        assertThatThrownBy(() -> new User(null, HASH, CREATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("username");
        assertThatThrownBy(() -> new User("hanako", null, CREATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("passwordHash");
        assertThatThrownBy(() -> new User("hanako", HASH, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
    }
}
