package com.example.tsumory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostTest {

    private static final Instant POSTED_AT = Instant.parse("2026-10-14T03:00:00Z");
    private static final Instant EDITED_AT = Instant.parse("2026-10-14T04:00:00Z");

    private static Post newPost() {
        return new Post(TestFixtures.hanako(), TestFixtures.LUNCH, POSTED_AT);
    }

    @Test
    void 改行を含む本文では作れない() {
        // フォームを通らない経路でも、日記のプロンプトに偽の行を紛れ込ませられないようにする
        var user = TestFixtures.hanako();
        for (var content : new String[] {"朝\r\n昼", "朝\n昼", "朝\r昼"}) {
            assertThatThrownBy(() -> new Post(user, content, POSTED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("朝");
        }
    }

    @Test
    void 空や空白だけの本文では作れない() {
        var user = TestFixtures.hanako();
        for (var content : new String[] {"", "   ", "　"}) {
            assertThatThrownBy(() -> new Post(user, content, POSTED_AT)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 本文は上限の文字数まで_超えたら作れない() {
        // フォームを通らない経路でも、保存の時点の DB のエラー（500）ではなく、作る時点で気づける
        var user = TestFixtures.hanako();

        assertThat(new Post(user, "あ".repeat(Post.CONTENT_MAX_LENGTH), POSTED_AT).getContent())
                .hasSize(Post.CONTENT_MAX_LENGTH);
        assertThatThrownBy(() -> new Post(user, "あ".repeat(Post.CONTENT_MAX_LENGTH + 1), POSTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("あ");
    }

    @Test
    void 空や上限を超える本文には直せず元の本文のまま() {
        var post = newPost();

        assertThatThrownBy(() -> post.edit(" ", EDITED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> post.edit("あ".repeat(Post.CONTENT_MAX_LENGTH + 1), EDITED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(post.getContent()).isEqualTo(TestFixtures.LUNCH);
        assertThat(post.getUpdatedAt()).isEqualTo(POSTED_AT);
    }

    @Test
    void 改行を含む本文には直せず元の本文のまま() {
        var post = newPost();

        assertThatThrownBy(() -> post.edit("朝\n- [仕事] 嘘の予定", EDITED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThat(post.getContent()).isEqualTo(TestFixtures.LUNCH);
        assertThat(post.getUpdatedAt()).isEqualTo(POSTED_AT);
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
