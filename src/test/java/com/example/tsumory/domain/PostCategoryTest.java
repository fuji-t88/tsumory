package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PostCategoryTest {

    @Test
    void カテゴリは仕様どおりの4つ() {
        assertThat(PostCategory.values())
                .containsExactly(PostCategory.WORK, PostCategory.PRIVATE, PostCategory.LEARNING, PostCategory.OTHER);
    }

    @ParameterizedTest
    @CsvSource({"WORK, 仕事", "PRIVATE, プライベート", "LEARNING, 学習", "OTHER, その他"})
    void 列挙子ごとに画面に出す日本語の表示名を持つ(PostCategory category, String displayName) {
        assertThat(category.getDisplayName()).isEqualTo(displayName);
    }

    @Test
    void DBの既定値OTHERと同じ名前の列挙子がある() {
        // V2__add_category_to_posts.sql の DEFAULT 'OTHER' を読み込めること
        assertThat(PostCategory.valueOf("OTHER")).isEqualTo(PostCategory.OTHER);
    }

    @Test
    void 名前はDBの列の長さに収まる() {
        // posts.category の長さは NAME_MAX_LENGTH。検査制約がないので、列挙子を足すときにここで長さを確かめる
        assertThat(Arrays.stream(PostCategory.values()).map(Enum::name))
                .allSatisfy(name -> assertThat(name).hasSizeLessThanOrEqualTo(PostCategory.NAME_MAX_LENGTH));
    }
}
