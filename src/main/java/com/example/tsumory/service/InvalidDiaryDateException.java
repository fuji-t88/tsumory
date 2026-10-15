package com.example.tsumory.service;

import java.time.LocalDate;

/** 日記を書けない日付（今日より後）を指定されたときに投げる。controller が受けて、メッセージを付けてホームへ戻す。 */
public class InvalidDiaryDateException extends RuntimeException {

    public InvalidDiaryDateException(LocalDate date) {
        super("Diary date is in the future: " + date);
    }
}
