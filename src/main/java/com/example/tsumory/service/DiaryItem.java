package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** 画面に出す日記。生成時刻は日本時間。 */
public record DiaryItem(LocalDate diaryDate, String content, LocalTime generatedTime) {

    public static DiaryItem from(Diary diary, ZoneId zone) {
        return new DiaryItem(
                diary.getDiaryDate(), diary.getContent(), LocalTime.ofInstant(diary.getGeneratedAt(), zone));
    }
}
