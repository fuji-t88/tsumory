package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PostCategoryTest {

    @Test
    void カテゴリは仕様どおりの4つ() {
        assertThat(PostCategory.values())
                .containsExactly(PostCategory.WORK, PostCategory.PRIVATE, PostCategory.LEARNING, PostCategory.OTHER);
    }

    @Test
    void 表示名() {
        assertThat(PostCategory.WORK.getDisplayName()).isEqualTo("仕事");
        assertThat(PostCategory.PRIVATE.getDisplayName()).isEqualTo("プライベート");
        assertThat(PostCategory.LEARNING.getDisplayName()).isEqualTo("学習");
        assertThat(PostCategory.OTHER.getDisplayName()).isEqualTo("その他");
    }

    @Test
    void DBの既定値OTHERと同じ名前の列挙子がある() {
        // V2__add_category_to_posts.sql の DEFAULT 'OTHER' を読み込めること
        assertThat(PostCategory.valueOf("OTHER")).isEqualTo(PostCategory.OTHER);
    }

    @Test
    void 名前はDBの列の長さに収まる() {
        // posts.category は VARCHAR(20)。検査制約がないので、列挙子を足すときにここで長さを確かめる
        assertThat(Arrays.stream(PostCategory.values()).map(Enum::name)).allMatch(name -> name.length() <= 20);
    }
}
