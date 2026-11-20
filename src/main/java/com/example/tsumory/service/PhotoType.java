package com.example.tsumory.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * 添付を受け付ける写真の形式（phase3-spec.md の「6. 処理の詳細 > 写真の検証」）。形式は、ブラウザが送った {@code Content-Type} や拡張子ではなく、
 * データの先頭のバイト（マジックナンバー）で決める。
 */
public enum PhotoType {
    JPEG("image/jpeg", "jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    private final String contentType;
    private final String formatName;
    private final byte[] signature;

    PhotoType(String contentType, String formatName, byte[] signature) {
        this.contentType = contentType;
        this.formatName = formatName;
        this.signature = signature;
    }

    /** 応答と保存に使う形式（{@code image/jpeg} など）。 */
    public String contentType() {
        return contentType;
    }

    /** ImageIO の読み込みに使う形式名。 */
    String formatName() {
        return formatName;
    }

    /** データの先頭のバイトから形式を決める。受け付けない形式なら空。 */
    public static Optional<PhotoType> detect(byte[] data) {
        return Arrays.stream(values()).filter(type -> type.matches(data)).findFirst();
    }

    /** 画面のメッセージに出す形式の名前（{@code JPEG か PNG}）。 */
    public static String displayNames() {
        return String.join(" か ", Arrays.stream(values()).map(Enum::name).toList());
    }

    /** 画面の {@code accept} に入れる形式の一覧（{@code image/jpeg,image/png}）。 */
    public static String acceptList() {
        return String.join(
                ",", Arrays.stream(values()).map(PhotoType::contentType).toList());
    }

    private boolean matches(byte[] data) {
        return data.length >= signature.length
                && Arrays.equals(data, 0, signature.length, signature, 0, signature.length);
    }
}
