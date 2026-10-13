package com.example.tsumory.service;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 日記を扱えない日付（今日と昨日以外）を指定されたときに投げる。 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DiaryNotFoundException extends RuntimeException {

    public DiaryNotFoundException(LocalDate date) {
        super("Diary date not available: " + date);
    }
}
