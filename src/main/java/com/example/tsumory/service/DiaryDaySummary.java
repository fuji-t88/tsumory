package com.example.tsumory.service;

import java.time.LocalDate;

/**
 * 日記一覧（リスト表示）の 1 日分。日記かつぶやきのどちらかがある日だけを作る。
 *
 * @param excerpt 日記の書き出し（先頭 60 文字。改行は空白に置き換え、超えたら末尾に「…」）。日記がなければ {@code null}
 * @param postCount その日（日本時間）のつぶやきの件数
 */
public record DiaryDaySummary(LocalDate date, boolean hasDiary, String excerpt, long postCount) {}
