package com.example.tsumory.service;

import static com.example.tsumory.domain.TestFixtures.jpegBytes;
import static com.example.tsumory.domain.TestFixtures.pngBytes;
import static com.example.tsumory.domain.TestFixtures.pngBytesOfSize;
import static com.example.tsumory.domain.TestFixtures.pngHeaderOnly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tsumory.service.InvalidPhotoException.Reason;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class PhotoValidatorTest {

    private static void assertRejected(ThrowingCallable call, Reason reason) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        InvalidPhotoException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    // --- 形式（PH-REQ-02） ---

    @Test
    void JPEGとPNGは受け付けデータから形式を決める() {
        assertThat(PhotoValidator.inspect(jpegBytes(40, 30))).isEqualTo(PhotoType.JPEG);
        assertThat(PhotoValidator.inspect(pngBytes(40, 30))).isEqualTo(PhotoType.PNG);
    }

    @Test
    void GIFは受け付けない() {
        var gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);

        assertRejected(() -> PhotoValidator.inspect(Arrays.copyOf(gif, 64)), Reason.UNSUPPORTED_TYPE);
    }

    @Test
    void HEICは受け付けない() {
        // HEIC は先頭 4 バイトの長さのあとに ftypheic が続く
        var heic = new byte[64];
        System.arraycopy("ftypheic".getBytes(StandardCharsets.US_ASCII), 0, heic, 4, 8);

        assertRejected(() -> PhotoValidator.inspect(heic), Reason.UNSUPPORTED_TYPE);
    }

    @Test
    void 拡張子やMIMEを偽った別の形式は受け付けない() {
        // 名前が .jpg でも、MIME が image/jpeg でも、中身が HTML やテキストなら断る（形式はデータの先頭で決める）
        var html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        var text = "昼はカレーだった".getBytes(StandardCharsets.UTF_8);

        assertRejected(() -> PhotoValidator.inspect(html), Reason.UNSUPPORTED_TYPE);
        assertRejected(() -> PhotoValidator.inspect(text), Reason.UNSUPPORTED_TYPE);
    }

    // --- 大きさ（PH-REQ-03） ---

    @Test
    void 空のファイルは受け付けない() {
        assertRejected(() -> PhotoValidator.inspect(new byte[0]), Reason.EMPTY);
    }

    @Test
    void 上限の大きさちょうどまで受け付け超えると受け付けない() {
        var atLimit = pngBytesOfSize(PhotoValidator.MAX_BYTES);
        var overLimit = pngBytesOfSize(PhotoValidator.MAX_BYTES + 1);

        assertThat(atLimit).hasSize(PhotoValidator.MAX_BYTES);
        assertThat(PhotoValidator.inspect(atLimit)).isEqualTo(PhotoType.PNG);
        assertRejected(() -> PhotoValidator.inspect(overLimit), Reason.TOO_LARGE);
    }

    // --- 中身（PH-REQ-09） ---

    @Test
    void 途中で切れた写真は受け付けない() {
        var jpeg = jpegBytes(400, 300);
        var png = pngBytes(400, 300);

        assertRejected(() -> PhotoValidator.inspect(Arrays.copyOf(jpeg, jpeg.length / 2)), Reason.BROKEN);
        assertRejected(() -> PhotoValidator.inspect(Arrays.copyOf(png, png.length / 2)), Reason.BROKEN);
    }

    @Test
    void 先頭だけが写真の形式で中身が壊れたデータは受け付けない() {
        var brokenJpeg = Arrays.copyOf(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01}, 256);

        assertRejected(() -> PhotoValidator.inspect(brokenJpeg), Reason.BROKEN);
    }

    @Test
    void 画素のデータがないPNGは受け付けない() {
        assertRejected(() -> PhotoValidator.inspect(pngHeaderOnly(400, 300)), Reason.BROKEN);
    }

    @Test
    void 画素数が上限を超える写真はデータを読む前に受け付けない() {
        // 20000 × 20000 と書いた PNG。実際に読み込むと大量のメモリを使う（解凍爆弾）ので、画素数で先に断る
        assertRejected(() -> PhotoValidator.inspect(pngHeaderOnly(20_000, 20_000)), Reason.TOO_MANY_PIXELS);
    }

    @Test
    void 幅と高さが上限以内でも画素数が上限を超える写真は受け付けない() {
        // 10000 × 6000 = 6000 万画素。幅と高さはどちらも上限以内
        assertRejected(() -> PhotoValidator.inspect(pngHeaderOnly(10_000, 6_000)), Reason.TOO_MANY_PIXELS);
    }

    @Test
    void 幅か高さが上限を超える写真は画素数が上限以内でも受け付けない() {
        assertRejected(
                () -> PhotoValidator.inspect(pngHeaderOnly(PhotoValidator.MAX_SIDE + 1, 1)), Reason.TOO_MANY_PIXELS);
        assertRejected(
                () -> PhotoValidator.inspect(pngHeaderOnly(1, PhotoValidator.MAX_SIDE + 1)), Reason.TOO_MANY_PIXELS);
    }

    @Test
    void 幅と高さと画素数が上限ちょうどなら画素数では断らない() {
        // 上限ちょうどの画像は大きすぎて作れないので、画素数の判定を通って、データがないことで断られることを確かめる
        int side = (int) Math.sqrt(PhotoValidator.MAX_PIXELS);

        assertRejected(() -> PhotoValidator.inspect(pngHeaderOnly(side, side)), Reason.BROKEN);
        assertRejected(() -> PhotoValidator.inspect(pngHeaderOnly(PhotoValidator.MAX_SIDE, 1)), Reason.BROKEN);
    }

    @Test
    void 大きな写真は間引いて読み込む() {
        assertThat(PhotoValidator.subsampling(1000, 1000)).isEqualTo(1);
        assertThat(PhotoValidator.subsampling(4000, 3000)).isEqualTo(4);
        assertThat(PhotoValidator.subsampling(1, 1)).isEqualTo(1);
    }
}
