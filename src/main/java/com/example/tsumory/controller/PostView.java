package com.example.tsumory.controller;

import com.example.tsumory.domain.PostCategory;

/** 画面に出すつぶやき 1 件。投稿時刻は表示用の文字列（{@code HH:mm}、日本時間）。 */
public record PostView(Long id, String content, String time, boolean edited, PostCategory category) {

    /** カテゴリのバッジの色（Bootstrap のクラス）。どれも文字と背景のコントラスト比 4.5:1 以上。「その他」は目立たない灰色。 */
    public String categoryBadgeClass() {
        return switch (category) {
            case WORK -> "text-bg-primary";
            case PRIVATE -> "text-bg-success";
            case LEARNING -> "text-bg-warning";
            case OTHER -> "text-bg-secondary";
        };
    }
}
