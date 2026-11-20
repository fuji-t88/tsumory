package com.example.tsumory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** つぶやきに添付した写真（phase3-spec.md の「4. ドメインモデル」）。1 つのつぶやきに 1 枚まで（主キーがつぶやきの ID）。 */
@Entity
@Table(name = "post_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostPhoto {

    /** 1 つのつぶやきに添付できる写真の枚数。DB では主キー（{@code post_id}）が守る。フォームの {@code @Size} がこの値を使う。 */
    public static final int MAX_PER_POST = 1;

    /** 写真の形式（{@code Content-Type}）の上限（文字数）。DB の列（{@code VARCHAR(100)}）と合わせる。 */
    public static final int CONTENT_TYPE_MAX_LENGTH = 100;

    @Id
    private Long postId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false, length = CONTENT_TYPE_MAX_LENGTH)
    private String contentType;

    @Column(nullable = false)
    private int sizeBytes;

    // 外から書き換えられないよう、getter は自分で書いて複製を返す
    @Getter(AccessLevel.NONE)
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * @throws NullPointerException 値が {@code null} のとき
     * @throws IllegalArgumentException 形式が空・空白だけ・{@link #CONTENT_TYPE_MAX_LENGTH} 文字を超えるとき、データが空のとき
     */
    public PostPhoto(Post post, String contentType, byte[] data, Instant now) {
        this.post = Objects.requireNonNull(post, "post");
        this.contentType = requireValidContentType(contentType);
        this.data = requireNotEmpty(data).clone();
        this.sizeBytes = this.data.length;
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    /** 写真のデータの複製を返す。 */
    public byte[] getData() {
        return data.clone();
    }

    private static String requireValidContentType(String contentType) {
        if (Objects.requireNonNull(contentType, "contentType").isBlank()) {
            throw new IllegalArgumentException("Photo content type must not be blank");
        }
        if (contentType.length() > CONTENT_TYPE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Photo content type must be at most " + CONTENT_TYPE_MAX_LENGTH + " characters");
        }
        return contentType;
    }

    private static byte[] requireNotEmpty(byte[] data) {
        if (Objects.requireNonNull(data, "data").length == 0) {
            throw new IllegalArgumentException("Photo data must not be empty");
        }
        return data;
    }
}
