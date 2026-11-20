package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 写真のデータを持つ record（{@link PhotoUpload}・{@link PhotoContent}）。配列を持つので、比較・複製・文字列化を自分で書いている。 */
class PhotoRecordsTest {

    private static final byte[] DATA = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

    @Test
    void 同じデータの写真は等しく別のデータなら等しくない() {
        assertThat(new PhotoUpload(DATA))
                .isEqualTo(new PhotoUpload(DATA.clone()))
                .hasSameHashCodeAs(new PhotoUpload(DATA));
        assertThat(new PhotoUpload(DATA)).isNotEqualTo(new PhotoUpload(new byte[] {1}));
        assertThat(new PhotoContent("image/jpeg", DATA))
                .isEqualTo(new PhotoContent("image/jpeg", DATA.clone()))
                .hasSameHashCodeAs(new PhotoContent("image/jpeg", DATA));
        assertThat(new PhotoContent("image/jpeg", DATA)).isNotEqualTo(new PhotoContent("image/png", DATA));
        assertThat(new PhotoContent("image/jpeg", DATA)).isNotEqualTo(new PhotoContent("image/jpeg", new byte[] {1}));
    }

    @Test
    void 渡したデータや受け取ったデータを書き換えても写真は変わらない() {
        var source = DATA.clone();
        var upload = new PhotoUpload(source);
        var content = new PhotoContent("image/jpeg", source);

        source[0] = 0;
        upload.data()[1] = 0;
        content.data()[1] = 0;

        assertThat(upload.data()).containsExactly(DATA);
        assertThat(content.data()).containsExactly(DATA);
    }

    @Test
    void 文字列にしても写真の中身は出さず大きさだけを出す() {
        // ログや例外のメッセージに写真のデータが出ないように
        assertThat(new PhotoUpload(DATA)).hasToString("PhotoUpload[size=4]");
        assertThat(new PhotoContent("image/jpeg", DATA)).hasToString("PhotoContent[contentType=image/jpeg, size=4]");
        assertThat(new PhotoUpload(DATA).size()).isEqualTo(4);
    }
}
