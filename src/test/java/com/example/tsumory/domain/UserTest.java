package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserTest {

    private static final Instant CREATED_AT = TestFixtures.jst(TestFixtures.REGISTERED_AT);

    private static final String HASH = TestFixtures.PASSWORD_HASH;

    // 例外のメッセージは、決まった文言と同じかで確かめる。どの決まりで拒否したかが分かり、渡した値（平文のパスワードなど）が
    // メッセージに入っていない（ログに出ない）ことも同時に確かめられる
    private static final String NOT_BCRYPT = "Password hash must be a BCrypt hash";
    private static final String USERNAME_BLANK = "Username must not be blank";
    private static final String USERNAME_TOO_LONG =
            "Username must be at most " + User.USERNAME_MAX_LENGTH + " characters";

    @Test
    void 必須の値がnullなら作れない() {
        // 保存の時点の DB のエラー（500）ではなく、作る時点で気づける
        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> new User(null, HASH, CREATED_AT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("username");
            softly.assertThatThrownBy(() -> new User("hanako", null, CREATED_AT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("passwordHash");
            softly.assertThatThrownBy(() -> new User("hanako", HASH, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("now");
        });
    }

    static Stream<Arguments> notHashes() {
        return Stream.of(
                argumentSet("平文", "password"),
                argumentSet("BCrypt の頭だけ付けた短い値", "$2a$10$hash"),
                argumentSet("空文字列", ""),
                argumentSet("ハッシュより 1 文字短い", HASH.substring(0, 59)),
                argumentSet("ハッシュより 1 文字長い", HASH + "x"));
    }

    @ParameterizedTest
    @MethodSource("notHashes")
    void 平文のパスワードでは作れない(String notHash) {
        // PasswordEncoder を通し忘れても、平文のパスワードを保存させない（CLAUDE.md の禁止事項「パスワード」）
        assertThatThrownBy(() -> new User("hanako", notHash, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(NOT_BCRYPT);
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
    void 上限の文字数までのユーザー名で作れる() {
        assertThat(new User("a".repeat(User.USERNAME_MAX_LENGTH), HASH, CREATED_AT).getUsername())
                .hasSize(User.USERNAME_MAX_LENGTH);
    }

    static Stream<Arguments> invalidUsernames() {
        return Stream.of(
                argumentSet("空文字列", "", USERNAME_BLANK),
                argumentSet("空白だけ", "  ", USERNAME_BLANK),
                argumentSet("上限より 1 文字長い", "a".repeat(User.USERNAME_MAX_LENGTH + 1), USERNAME_TOO_LONG));
    }

    @ParameterizedTest
    @MethodSource("invalidUsernames")
    void 空や上限を超えるユーザー名では作れない(String username, String message) {
        assertThatThrownBy(() -> new User(username, HASH, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }
}
