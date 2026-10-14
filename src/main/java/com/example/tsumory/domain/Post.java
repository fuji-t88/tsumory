package com.example.tsumory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant postedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // DB の既定値は INSERT で列を省いたときだけ効く。JPA は値を明示して INSERT するので、初期値で OTHER を持たせる
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostCategory category = PostCategory.OTHER;

    /** ユーザーが手でカテゴリを直したら true。true のあいだは AI の判定で上書きしない（判断は service が行う）。 */
    @Column(nullable = false)
    private boolean categorySetByUser = false;

    public Post(User user, String content, Instant now) {
        this.user = user;
        this.content = content;
        this.postedAt = now;
        this.updatedAt = now;
    }

    /** 本文と更新時刻を変える。投稿時刻とカテゴリは変えない（判定し直すまでは前のカテゴリのまま）。 */
    public void edit(String content, Instant now) {
        this.content = content;
        this.updatedAt = now;
    }

    /**
     * カテゴリを割り当てる。AI の判定を反映するときと、ユーザーが手で直すときの両方で使う。更新時刻は変えない（カテゴリを直しても「編集済み」にしない）。
     *
     * <p>誰が呼んだかは区別しない。手で直したカテゴリを AI に上書きさせないのは、呼び出す service の役目（{@link #isCategorySetByUser()} を見る）。
     */
    public void assignCategory(PostCategory category) {
        this.category = Objects.requireNonNull(category, "category");
    }

    /** ユーザーが手でカテゴリを直したことを記録する。手で直す service が {@link #assignCategory} と一緒に呼ぶ。 */
    public void markCategorySetByUser() {
        this.categorySetByUser = true;
    }

    public boolean isEdited() {
        return updatedAt.isAfter(postedAt);
    }
}
