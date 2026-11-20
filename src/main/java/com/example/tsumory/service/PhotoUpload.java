package com.example.tsumory.service;

import java.util.Arrays;
import java.util.Objects;

/**
 * つぶやきに添付する写真。controller が受け取ったファイルから作り、{@link PostService} に渡す（service は Web の型を知らない。
 * phase3-spec.md の「6. 処理の詳細 > つぶやきの投稿」）。形式は、ブラウザが送ったものを使わず、{@link PhotoValidator} がデータから決める。
 *
 * @param data 写真のデータ。受け取るときと返すときに複製する
 */
public record PhotoUpload(byte[] data) {

    public PhotoUpload {
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
        return other instanceof PhotoUpload(var otherData) && Arrays.equals(data, otherData);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    /** データの中身は出さない（ログに写真が出ないように）。 */
    @Override
    public String toString() {
        return "PhotoUpload[size=" + data.length + "]";
    }
}
