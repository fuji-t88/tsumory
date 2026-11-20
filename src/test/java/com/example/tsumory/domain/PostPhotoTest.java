package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostPhotoTest {

    private static final Instant SAVED_AT = TestFixtures.jst("2026-10-08T12:00:00");
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

    private static final String BLANK_TYPE = "Photo content type must not be blank";
    private static final String TOO_LONG_TYPE =
            "Photo content type must be at most " + PostPhoto.CONTENT_TYPE_MAX_LENGTH + " characters";
    private static final String EMPTY_DATA = "Photo data must not be empty";

    private final Post post = TestFixtures.post(TestFixtures.hanako(), TestFixtures.LUNCH, "2026-10-08T12:00:00");

    @Test
    void つぶやきと形式とデータと保存した日時で作れる() {
        var photo = new PostPhoto(post, "image/jpeg", JPEG_BYTES, SAVED_AT);

        assertSoftly(softly -> {
            softly.assertThat(photo.getPost()).isSameAs(post);
            softly.assertThat(photo.getContentType()).isEqualTo("image/jpeg");
            softly.assertThat(photo.getData()).containsExactly(JPEG_BYTES);
            softly.assertThat(photo.getSizeBytes()).isEqualTo(JPEG_BYTES.length);
            softly.assertThat(photo.getCreatedAt()).isEqualTo(SAVED_AT);
        });
    }

    @Test
    void 必須の値がnullなら作れない() {
        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> new PostPhoto(null, "image/jpeg", JPEG_BYTES, SAVED_AT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("post");
            softly.assertThatThrownBy(() -> new PostPhoto(post, null, JPEG_BYTES, SAVED_AT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("contentType");
            softly.assertThatThrownBy(() -> new PostPhoto(post, "image/jpeg", null, SAVED_AT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("data");
            softly.assertThatThrownBy(() -> new PostPhoto(post, "image/jpeg", JPEG_BYTES, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("now");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void 形式が空や空白だけなら作れない(String contentType) {
        assertThatThrownBy(() -> new PostPhoto(post, contentType, JPEG_BYTES, SAVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(BLANK_TYPE);
    }

    @Test
    void 形式は上限の文字数ちょうどまで作れて超えると作れない() {
        var atLimit = "a".repeat(PostPhoto.CONTENT_TYPE_MAX_LENGTH);

        assertThat(new PostPhoto(post, atLimit, JPEG_BYTES, SAVED_AT).getContentType())
                .isEqualTo(atLimit);
        assertThatThrownBy(() -> new PostPhoto(post, atLimit + "a", JPEG_BYTES, SAVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(TOO_LONG_TYPE);
    }

    @Test
    void データが空なら作れない() {
        assertThatThrownBy(() -> new PostPhoto(post, "image/jpeg", new byte[0], SAVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EMPTY_DATA);
    }

    @Test
    void 渡したデータをあとで書き換えても写真は変わらない() {
        var source = JPEG_BYTES.clone();
        var photo = new PostPhoto(post, "image/jpeg", source, SAVED_AT);

        source[0] = 0;

        assertThat(photo.getData()).containsExactly(JPEG_BYTES);
    }

    @Test
    void 受け取ったデータを書き換えても写真は変わらない() {
        var photo = new PostPhoto(post, "image/jpeg", JPEG_BYTES, SAVED_AT);

        photo.getData()[0] = 0;

        assertThat(photo.getData()).containsExactly(JPEG_BYTES);
    }
}
