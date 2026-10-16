package com.example.tsumory.form;

import jakarta.validation.constraints.Size;

/**
 * 検索フォーム（{@code GET /search?q=...&category=...}）。
 *
 * @param q キーワード。前後の空白は controller で取り除く
 * @param category カテゴリの名前（{@code WORK} など）。候補にない値や空は「すべて」として扱う（エラーにしない）ので、文字列で受ける
 */
public record SearchForm(
        @Size(max = SearchForm.KEYWORD_MAX_LENGTH, message = "{max} 文字以内で入力してください")
        String q,

        String category) {

    /** キーワードの上限（文字数）。日記一覧（{@code GET /diary}）のキーワードも同じ。画面の {@code maxlength} は {@code InputLimitsAdvice} が渡す。 */
    public static final int KEYWORD_MAX_LENGTH = 50;
}
