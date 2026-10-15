package com.example.tsumory.service;

import java.time.YearMonth;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 日記一覧で開けない月（登録月より前・今月より後）を指定されたときに投げる。 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DiaryMonthNotFoundException extends RuntimeException {

    public DiaryMonthNotFoundException(YearMonth month) {
        super("Diary month not available: " + month);
    }
}
