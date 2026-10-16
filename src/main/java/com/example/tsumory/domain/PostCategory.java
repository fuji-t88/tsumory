package com.example.tsumory.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * つぶやきのカテゴリ。DB の {@code posts.category} には列挙子の名前をそのまま入れ、表示名は入れない（phase2-spec.md の
 * 「3. データベース > カテゴリ」「3. データベース > `posts`（追加する列）」）。
 */
@Getter
@RequiredArgsConstructor
public enum PostCategory {
    WORK("仕事"),
    PRIVATE("プライベート"),
    LEARNING("学習"),
    /** どれにも当てはまらない、またはどれとも決められないもの。既定値で、AI の判定に失敗したときもこれにする。 */
    OTHER("その他");

    /** 列挙子の名前（DB に入れる値）の上限（文字数）。列の長さ（{@code VARCHAR(20)}）と同じ。変えるときは、DB の列もマイグレーションで変える。 */
    public static final int NAME_MAX_LENGTH = 20;

    /** 画面に出す日本語の表示名。 */
    private final String displayName;
}
