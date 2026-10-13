package com.example.tsumory.service;

import java.time.LocalDate;
import java.util.List;

/** その日のつぶやきの本文から、日記の本文を書く。 */
public interface DiaryWriter {

    /**
     * @param date 日記の日付
     * @param contents その日のつぶやきの本文（投稿時刻の古い順）
     * @return 日記の本文
     * @throws DiaryRefusedException 書くことを断られたとき
     * @throws DiaryWriteFailedException エラーやタイムアウトで書けなかったとき
     */
    String write(LocalDate date, List<String> contents);
}
