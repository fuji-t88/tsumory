package com.example.tsumory.form;

import com.example.tsumory.domain.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 投稿フォーム。本文に改行と見えない文字（{@link Post#INVISIBLE_CHAR_CLASS}）を入れさせない（{@code Post} でも同じ値を拒否する。理由は
 * phase2-spec.md の「5. 画面 > 入力チェック（変更。投稿・編集共通）」）。
 */
public record PostForm(
        @NotBlank(message = "内容を入力してください")
        @Size(max = Post.CONTENT_MAX_LENGTH, message = "{max} 文字以内で入力してください")
        @Pattern(regexp = "[^\r\n]*", message = "改行は入れられません")
        @Pattern(regexp = Post.NO_INVISIBLE_CHARS_REGEX, message = "見えない文字や制御文字は入れられません")
        String content) {}
