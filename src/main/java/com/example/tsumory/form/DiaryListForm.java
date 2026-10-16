package com.example.tsumory.form;

import jakarta.validation.constraints.Size;

/**
 * 日記一覧の絞り込み（{@code GET /diary?keyword=...}）。キーワードの決まりは検索画面（{@link SearchForm}）と同じ。
 *
 * @param keyword 本文で絞り込むキーワード。前後の空白を取り除いてから検証する（指定がなければ空文字。絞り込まない）
 */
public record DiaryListForm(
        @Size(max = SearchForm.KEYWORD_MAX_LENGTH, message = "{max} 文字以内で入力してください")
        String keyword) {

    public DiaryListForm {
        keyword = keyword == null ? "" : keyword.strip();
    }
}
