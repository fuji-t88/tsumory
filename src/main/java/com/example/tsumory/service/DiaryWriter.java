package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import java.time.LocalDate;
import java.util.List;

/** その日のつぶやきから、日記の本文を書く。 */
public interface DiaryWriter {

    /**
     * その日のつぶやきから、日記の本文を書く。
     *
     * @param date 日記の日付
     * @param posts その日のつぶやき（投稿時刻の古い順）。本文とカテゴリを使う
     * @return 日記の本文
     * @throws DiaryGenerationException エラー・タイムアウトや、書くことを断られたなどで書けなかったとき
     */
    String write(LocalDate date, List<Post> posts);
}
