package com.example.tsumory.service;

import java.util.List;

/** 日記一覧（カレンダー表示）の 1 週。日曜から土曜までの 7 マス。 */
public record DiaryCalendarWeek(List<DiaryCalendarDay> days) {}
