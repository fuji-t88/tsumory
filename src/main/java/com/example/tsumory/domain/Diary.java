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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "diaries", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "diary_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** 日本時間の日付 */
    @Column(nullable = false, updatable = false)
    private LocalDate diaryDate;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private Instant generatedAt;

    /** @throws NullPointerException 値が {@code null} のとき */
    public Diary(User user, LocalDate diaryDate, String content, Instant now) {
        this.user = Objects.requireNonNull(user, "user");
        this.diaryDate = Objects.requireNonNull(diaryDate, "diaryDate");
        this.content = Objects.requireNonNull(content, "content");
        this.generatedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * 再生成した本文に置き換え、生成時刻を更新する。
     *
     * @throws NullPointerException 値が {@code null} のとき
     */
    public void updateContent(String content, Instant now) {
        // 先に両方を確かめる（本文だけ書き換わった状態で止まらないように）
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(now, "now");
        this.content = content;
        this.generatedAt = now;
    }
}
