package com.example.tsumory.service;

import java.util.Arrays;
import java.util.Objects;

/**
 * つぶやきに添付する写真。controller が受け取ったファイルから作り、{@link PostService#create} に渡す（service は Web の型を知らない。
 * phase3-spec.md の「6. 処理の詳細 > つぶやきの投稿」）。
 *
 * @param contentType 写真の形式（ブラウザが送った {@code Content-Type}）
 * @param data 写真のデータ。受け取るときと返すときに複製する
 */
public record PhotoUpload(String contentType, byte[] data) {

    public PhotoUpload {
        Objects.requireNonNull(contentType, "contentType");
        data = Objects.requireNonNull(data, "data").clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    /** 写真の大きさ（バイト）。 */
    public int size() {
        return data.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PhotoUpload(var otherType, var otherData)
                && contentType.equals(otherType)
                && Arrays.equals(data, otherData);
    }

    @Override
    public int hashCode() {
        return 31 * contentType.hashCode() + Arrays.hashCode(data);
    }

    /** データの中身は出さない（ログに写真が出ないように）。 */
    @Override
    public String toString() {
        return "PhotoUpload[contentType=" + contentType + ", size=" + data.length + "]";
    }
}
