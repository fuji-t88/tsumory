package com.example.tsumory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /** ログインに使う名前の上限（文字数）。列の長さ（{@code VARCHAR(50)}）と同じ。変えるときは、DB の列もマイグレーションで変える。 */
    public static final int USERNAME_MAX_LENGTH = 50;

    /**
     * BCrypt のハッシュの形（{@code $2a$}・{@code $2b$}・{@code $2y$} + コスト 2 桁 + {@code $} + ソルトとハッシュの 53 文字。全体で 60 文字）。
     * 平文のパスワードを {@code passwordHash} に入れさせないため（CLAUDE.md の禁止事項「パスワード」）。
     */
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = USERNAME_MAX_LENGTH)
    private String username;

    @Column(nullable = false, length = 72)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * @throws NullPointerException 値が {@code null} のとき
     * @throws IllegalArgumentException {@code username} が空・{@link #USERNAME_MAX_LENGTH} 文字を超えるとき、
     *     または {@code passwordHash} が BCrypt のハッシュの形でないとき（平文のパスワードなど）
     */
    public User(String username, String passwordHash, Instant now) {
        this.username = requireValidUsername(username);
        this.passwordHash = requireBcryptHash(passwordHash);
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    // 保存の時点の DB のエラーではなく、作る時点で気づけるようにする。値はメッセージに入れない
    private static String requireValidUsername(String username) {
        if (Objects.requireNonNull(username, "username").isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (username.length() > USERNAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Username must be at most " + USERNAME_MAX_LENGTH + " characters");
        }
        return username;
    }

    // ハッシュ化し忘れた平文のパスワードを保存させない。値はメッセージに入れない（平文のパスワードがログに出ないように）
    private static String requireBcryptHash(String passwordHash) {
        if (!BCRYPT_HASH
                .matcher(Objects.requireNonNull(passwordHash, "passwordHash"))
                .matches()) {
            throw new IllegalArgumentException("Password hash must be a BCrypt hash");
        }
        return passwordHash;
    }
}
