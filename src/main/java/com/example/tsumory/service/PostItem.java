package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import java.time.LocalTime;
import java.time.ZoneId;

/** 画面に出すつぶやき 1 件。投稿時刻は日本時間。 */
public record PostItem(Long id, String content, LocalTime postedTime, boolean edited, PostCategory category) {

    static PostItem from(Post post, ZoneId zone) {
        return new PostItem(
                post.getId(),
                post.getContent(),
                LocalTime.ofInstant(post.getPostedAt(), zone),
                post.isEdited(),
                post.getCategory());
    }
}
