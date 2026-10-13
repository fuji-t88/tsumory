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

    public Diary(User user, LocalDate diaryDate, String content, Instant now) {
        this.user = user;
        this.diaryDate = diaryDate;
        this.content = content;
        this.generatedAt = now;
    }

    /** 再生成した本文に置き換え、生成時刻を更新する。 */
    public void updateContent(String content, Instant now) {
        this.content = content;
        this.generatedAt = now;
    }
}
