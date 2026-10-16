package com.example.tsumory.service;

/**
 * 検索キーワードを {@code LIKE} に渡す形にする。つぶやき（{@link PostService#searchPosts}）と日記（{@link DiaryService#searchDiaries}）の
 * 検索で同じ決まりを使うため、1 か所にまとめる（クエリはどちらも {@code ESCAPE '!'}）。
 */
final class LikeKeyword {

    private LikeKeyword() {}

    /**
     * {@code !}・{@code %}・{@code _} の前に {@code !} を付ける。ユーザーが入力した {@code %}・{@code _} を、全件や任意の 1 文字に一致させないため。
     * {@code !} 自身を先に置き換えないと、付けた {@code !} まで二重にエスケープされる。
     */
    static String escape(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
