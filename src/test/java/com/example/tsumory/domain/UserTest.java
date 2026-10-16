package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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

    @Test
    void 平文のパスワードでは作れない() {
        // PasswordEncoder を通し忘れても、平文のパスワードを保存させない（CLAUDE.md の禁止事項「パスワード」）
        for (var notHash : new String[] {"password", "$2a$10$hash", "", HASH.substring(0, 59), HASH + "x"}) {
            assertThatThrownBy(() -> new User("hanako", notHash, CREATED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    // 値はメッセージに入れない（平文のパスワードがログに出ないように）
                    .hasMessageNotContaining("password")
                    .hasMessage("Password hash must be a BCrypt hash");
        }
    }

    @Test
    void BCryptPasswordEncoderのハッシュで作れる() {
        var hash = new BCryptPasswordEncoder().encode("correct horse battery staple");

        assertThat(new User("hanako", hash, CREATED_AT).getPasswordHash()).isEqualTo(hash);
        // $2b$・$2y$ の形のハッシュも受け付ける（ほかのツールで作ったハッシュ）
        assertThat(new User("hanako", "$2b$" + HASH.substring(4), CREATED_AT).getPasswordHash())
                .startsWith("$2b$");
        assertThat(new User("hanako", "$2y$" + HASH.substring(4), CREATED_AT).getPasswordHash())
                .startsWith("$2y$");
    }

    @Test
    void ユーザー名は空でなく上限の文字数まで() {
        assertThat(new User("a".repeat(User.USERNAME_MAX_LENGTH), HASH, CREATED_AT).getUsername())
                .hasSize(User.USERNAME_MAX_LENGTH);
        for (var username : new String[] {"", "  ", "a".repeat(User.USERNAME_MAX_LENGTH + 1)}) {
            assertThatThrownBy(() -> new User(username, HASH, CREATED_AT)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
