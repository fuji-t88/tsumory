package com.example.tsumory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
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

    public Post(User user, String content, Instant now) {
        this.user = user;
        this.content = content;
        this.postedAt = now;
        this.updatedAt = now;
    }

    /** 本文と更新時刻を変える。投稿時刻は変えない。 */
    public void edit(String content, Instant now) {
        this.content = content;
        this.updatedAt = now;
    }

    public boolean isEdited() {
        return updatedAt.isAfter(postedAt);
    }
}
