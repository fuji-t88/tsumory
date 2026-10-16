package com.example.tsumory.form;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 編集モードのフォーム。本文の検証は {@link PostForm} と同じ（改行も入れさせない）。カテゴリは手で直すときに使う（投稿フォームでは受け取らない）。 */
public record PostEditForm(
        @NotBlank(message = "内容を入力してください")
        @Size(max = Post.CONTENT_MAX_LENGTH, message = "{max} 文字以内で入力してください")
        @Pattern(regexp = "[^\r\n]*", message = "改行は入れられません")
        String content,

        @NotNull(message = "カテゴリを選んでください") PostCategory category) {}
