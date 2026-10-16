package com.example.tsumory.controller;

import com.example.tsumory.domain.Post;
import com.example.tsumory.form.SearchForm;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 入力の上限（文字数）を、すべての画面のテンプレートに渡す。画面の {@code maxlength} を数字で書かず、form・エンティティと同じ定数から出すため
 * （上限を変えたとき、画面とサーバーの判定がずれないように）。
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
