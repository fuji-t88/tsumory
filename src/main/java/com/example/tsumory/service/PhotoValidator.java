package com.example.tsumory.service;

import com.example.tsumory.service.InvalidPhotoException.Reason;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * 添付された写真を、保存してよいか確かめる（phase3-spec.md の「6. 処理の詳細 > 写真の検証」）。大きさ・形式・画素数・中身（画像として読めるか）を、
 * この順に見る。上限は仕様が変わりうる業務ルールなので、エンティティでも DB でもなく、ここに置く。
 */
public final class PhotoValidator {

    /** 写真 1 枚の大きさの上限（バイト）。5MB。 */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /** 写真の幅・高さの上限（画素）。 */
    public static final int MAX_SIDE = 12_000;

    /** 写真の画素数（幅 × 高さ）の上限。5000 万画素。 */
    public static final long MAX_PIXELS = 50_000_000L;

    /** 壊れていないかを確かめるために読み込むときの画素数の目安。これを超える写真は間引いて読む（メモリを使いすぎないため）。 */
    private static final long DECODE_PIXELS = 1_000_000L;

    private PhotoValidator() {}

    /**
     * 写真を確かめ、形式を返す。形式は、データの先頭のバイトで決める（ブラウザが送った形式は使わない）。
     *
     * @throws InvalidPhotoException 空・{@link #MAX_BYTES} を超える・JPEG と PNG 以外・画素数が上限を超える・画像として読めないとき
     */
    public static PhotoType inspect(byte[] data) {
        if (data.length == 0) {
            throw new InvalidPhotoException(Reason.EMPTY);
        }
        if (data.length > MAX_BYTES) {
            throw new InvalidPhotoException(Reason.TOO_LARGE);
        }
        var type = PhotoType.detect(data).orElseThrow(() -> new InvalidPhotoException(Reason.UNSUPPORTED_TYPE));
        var readers = ImageIO.getImageReadersByFormatName(type.formatName());
        if (!readers.hasNext()) {
            // JDK に入っている読み込みの部品がない（jlink でモジュールを外した場合など）。写真ではなく環境の問題
            throw new IllegalStateException("No image reader for " + type);
        }
        var reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            readFully(reader, input);
        } catch (InvalidPhotoException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // 壊れたデータでは、ImageIO が IOException 以外の実行時例外も投げる
            throw new InvalidPhotoException(Reason.BROKEN, e);
        } finally {
            reader.dispose();
        }
        return type;
    }

    /** 画素数を確かめてから、間引いて最後まで読む。途中で切れたデータは、例外にならず警告になるので、警告も壊れているとみなす。 */
    private static void readFully(ImageReader reader, ImageInputStream input) throws IOException {
        reader.setInput(input, true, true);
        var warnings = new ArrayList<String>();
        reader.addIIOReadWarningListener((source, warning) -> warnings.add(warning));
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0) {
            throw new InvalidPhotoException(Reason.BROKEN);
        }
        if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
            throw new InvalidPhotoException(Reason.TOO_MANY_PIXELS);
        }
        var param = reader.getDefaultReadParam();
        int step = subsampling(width, height);
        param.setSourceSubsampling(step, step, 0, 0);
        if (reader.read(0, param) == null || !warnings.isEmpty()) {
            throw new InvalidPhotoException(Reason.BROKEN);
        }
    }

    /** 読み込む画素数が {@link #DECODE_PIXELS} 程度になる間引きの間隔。 */
    static int subsampling(int width, int height) {
        return (int) Math.max(1, Math.ceil(Math.sqrt((double) width * height / DECODE_PIXELS)));
    }
}
