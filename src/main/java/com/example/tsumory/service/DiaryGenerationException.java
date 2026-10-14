package com.example.tsumory.service;

/** AI の呼び出しが失敗して日記を書けなかったときに投げる（API エラー、タイムアウト、書くことを断られた場合など）。 */
public class DiaryGenerationException extends RuntimeException {

    public DiaryGenerationException(String message) {
        super(message);
    }

    public DiaryGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
