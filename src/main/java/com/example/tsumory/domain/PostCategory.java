package com.example.tsumory.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * つぶやきのカテゴリ（docs/phase2-spec.md の「3. データベース > カテゴリ」）。
 *
 * <p>DB の {@code posts.category} には列挙子の名前（{@link #name()}）をそのまま入れる。DB に検査制約はないので、値が 4 つのどれかであることは
 * この列挙型で守る。表示名（{@link #getDisplayName()}）は DB に入れない（表示名を変えてもデータを直さずに済むため）。
 */
@Getter
@RequiredArgsConstructor
public enum PostCategory {
    WORK("仕事"),
    PRIVATE("プライベート"),
    LEARNING("学習"),
    /** どれにも当てはまらない、またはどれとも決められないもの。既定値で、AI の判定に失敗したときもこれにする。 */
    OTHER("その他");

    /**
     * 列挙子の名前（DB に入れる値）の上限（文字数）。{@code posts.category} の列の長さ（{@code VARCHAR(20)}）と同じ。変えるときは、DB の列も
     * マイグレーションで変える。DB に検査制約はないので、列挙子を足すときは {@code PostCategoryTest} がこの長さに収まるかを確かめる。
     */
    public static final int NAME_MAX_LENGTH = 20;

    /** 画面に出す日本語の表示名。 */
    private final String displayName;
}
