package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostTest {

    private static final Instant POSTED_AT = Instant.parse("2026-10-14T03:00:00Z");
    private static final Instant EDITED_AT = Instant.parse("2026-10-14T04:00:00Z");

    private static Post newPost() {
        return new Post(new User("demo", "hash", POSTED_AT), "昼のカレーが辛すぎた", POSTED_AT);
    }

    @Test
    void 投稿時のカテゴリはOTHERで手で直していない() {
        var post = newPost();

        assertThat(post.getCategory()).isEqualTo(PostCategory.OTHER);
        assertThat(post.isCategorySetByUser()).isFalse();
    }

    @Test
    void カテゴリを割り当てても更新時刻は変わらない() {
        var post = newPost();

        post.assignCategory(PostCategory.PRIVATE);

        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
        assertThat(post.getUpdatedAt()).isEqualTo(POSTED_AT);
        assertThat(post.isEdited()).isFalse();
    }

    @Test
    void 割り当てただけでは手で直した印は立たない() {
        // AI の判定も assignCategory を使うので、印は手で直す service が markCategorySetByUser で立てる
        var post = newPost();

        post.assignCategory(PostCategory.WORK);

        assertThat(post.isCategorySetByUser()).isFalse();
    }

    @Test
    void 手で直した印を立てられる() {
        var post = newPost();

        post.assignCategory(PostCategory.LEARNING);
        post.markCategorySetByUser();

        assertThat(post.getCategory()).isEqualTo(PostCategory.LEARNING);
        assertThat(post.isCategorySetByUser()).isTrue();
        assertThat(post.getUpdatedAt()).isEqualTo(POSTED_AT);
    }

    @Test
    void 本文を編集してもカテゴリは変わらない() {
        var post = newPost();
        post.assignCategory(PostCategory.PRIVATE);

        post.edit("昼のカレーが辛すぎた。でもまた行く", EDITED_AT);

        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
        assertThat(post.isCategorySetByUser()).isFalse();
        assertThat(post.isEdited()).isTrue();
    }

    @Test
    void カテゴリにnullは入れられない() {
        var post = newPost();

        assertThatThrownBy(() -> post.assignCategory(null)).isInstanceOf(NullPointerException.class);
        assertThat(post.getCategory()).isEqualTo(PostCategory.OTHER);
    }
}
