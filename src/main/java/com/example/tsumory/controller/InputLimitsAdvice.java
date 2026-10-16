package com.example.tsumory.controller;

import com.example.tsumory.domain.Post;
import com.example.tsumory.form.SearchForm;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 入力の上限（文字数）を、すべての画面のテンプレートに渡す。画面の {@code maxlength} も form・エンティティと同じ定数から出す（CLAUDE.md の
 * 「入力の検証（多層防御）」）。
 */
@ControllerAdvice
public class InputLimitsAdvice {

    @ModelAttribute("postContentMaxLength")
    public int postContentMaxLength() {
        return Post.CONTENT_MAX_LENGTH;
    }

    @ModelAttribute("keywordMaxLength")
    public int keywordMaxLength() {
        return SearchForm.KEYWORD_MAX_LENGTH;
    }
}
