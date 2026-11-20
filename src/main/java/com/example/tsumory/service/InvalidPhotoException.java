package com.example.tsumory.service;

/** 添付された写真を受け付けられないとき。理由ごとに、画面で別のメッセージを出す（phase3-spec.md の「6. 処理の詳細 > 写真の検証」）。 */
public class InvalidPhotoException extends RuntimeException {

    /** 受け付けない理由。 */
    public enum Reason {
        /** ファイルが空。 */
        EMPTY,
        /** 大きさ（バイト）が上限を超える。 */
        TOO_LARGE,
        /** JPEG・PNG でない（拡張子や MIME を偽った別の形式を含む）。 */
        UNSUPPORTED_TYPE,
        /** 画素数が上限を超える。 */
        TOO_MANY_PIXELS,
        /** 画像として読めない（壊れている、途中で切れている）。 */
        BROKEN
    }

    private final Reason reason;

    public InvalidPhotoException(Reason reason) {
        this(reason, null);
    }

    public InvalidPhotoException(Reason reason, Throwable cause) {
        super("Invalid photo: " + reason, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
