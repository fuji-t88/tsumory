package com.example.tsumory.controller;

import com.example.tsumory.service.DiaryItem;
import java.time.LocalDate;
import java.util.List;

/** 今日のつぶやき画面の 1 日分の区画（今日・昨日）。日記がなければ {@code diary} は {@code null}。 */
public record DaySection(LocalDate date, List<PostView> posts, DiaryItem diary) {

    public boolean hasDiary() {
        return diary != null;
    }

    /** つぶやきも日記もない日。昨日の区画はこのとき出さない。 */
    public boolean isEmpty() {
        return posts.isEmpty() && diary == null;
    }
}
