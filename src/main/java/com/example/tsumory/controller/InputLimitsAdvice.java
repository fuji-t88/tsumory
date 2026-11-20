package com.example.tsumory.controller;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostPhoto;
import com.example.tsumory.form.SearchForm;
import com.example.tsumory.service.InvalidPhotoException;
import com.example.tsumory.service.PhotoType;
import com.example.tsumory.service.PhotoValidator;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 入力の上限（文字数・写真の枚数・形式・大きさ）を、すべての画面のテンプレートに渡す。画面の {@code maxlength} も form・エンティティと同じ定数から出す（CLAUDE.md の
 * 「入力の検証（多層防御）」）。
 */
@ControllerAdvice
public class InputLimitsAdvice {

    @ModelAttribute("postContentMaxLength")
    public int postContentMaxLength() {
        return Post.CONTENT_MAX_LENGTH;
    }

    @ModelAttribute("photoMaxPerPost")
    public int photoMaxPerPost() {
        return PostPhoto.MAX_PER_POST;
    }

    /** 写真の欄の {@code accept}（受け付ける形式）。 */
    @ModelAttribute("photoAccept")
    public String photoAccept() {
        return PhotoType.acceptList();
    }

    /** 写真の大きさの上限（バイト）。ブラウザで送る前に確かめる（{@code photo-input.js}）。 */
    @ModelAttribute("photoMaxBytes")
    public int photoMaxBytes() {
        return PhotoValidator.MAX_BYTES;
    }

    /** 写真の欄の案内（受け付ける形式と大きさ）。 */
    @ModelAttribute("photoHint")
    public String photoHint() {
        return PhotoType.displayNames() + "・" + PhotoValidator.MAX_BYTES / (1024 * 1024) + "MB まで";
    }

    /** ブラウザで送る前に断るときのメッセージ。サーバーで断るときと同じ文言にする。 */
    @ModelAttribute("photoTypeMessage")
    public String photoTypeMessage() {
        return PostController.photoErrorMessage(InvalidPhotoException.Reason.UNSUPPORTED_TYPE);
    }

    @ModelAttribute("photoTooLargeMessage")
    public String photoTooLargeMessage() {
        return PostController.photoErrorMessage(InvalidPhotoException.Reason.TOO_LARGE);
    }

    @ModelAttribute("keywordMaxLength")
    public int keywordMaxLength() {
        return SearchForm.KEYWORD_MAX_LENGTH;
    }
}
