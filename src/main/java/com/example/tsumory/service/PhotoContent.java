package com.example.tsumory.service;

import java.util.Arrays;
import java.util.Objects;

/**
 * 画面に返す写真（phase3-spec.md の「6. 処理の詳細 > 写真の取得」）。
 *
 * @param contentType 保存したときにデータから決めた形式（{@code image/jpeg} か {@code image/png}）
 * @param data 写真のデータ。受け取るときと返すときに複製する
 */
public record PhotoContent(String contentType, byte[] data) {

    public PhotoContent {
        Objects.requireNonNull(contentType, "contentType");
        data = Objects.requireNonNull(data, "data").clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PhotoContent(var otherType, var otherData)
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
        return "PhotoContent[contentType=" + contentType + ", size=" + data.length + "]";
    }
}
