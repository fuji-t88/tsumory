package com.example.tsumory.controller;

import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.service.PostItem;
import java.time.format.DateTimeFormatter;

/** 画面に出すつぶやき 1 件。投稿時刻は表示用の文字列（{@code HH:mm}、日本時間）。 */
public record PostView(Long id, String time, String content, PostCategory category) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /** 投稿時刻を表示用の文字列にして詰める。今日のつぶやき画面と日記表示で使う。 */
    static PostView from(PostItem post) {
        return new PostView(post.id(), post.postedTime().format(TIME_FORMAT), post.content(), post.category());
    }
}
