package com.example.tsumory.form;

import com.example.tsumory.domain.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 投稿フォーム。
 *
 * <p>本文に改行を入れさせない。日記のプロンプトでは 1 行に 1 つぶやきを並べるので、改行で偽の行を紛れ込ませられないようにする（{@code Post} でも同じ値を拒否する）。
 * 見えない文字（{@link Post#INVISIBLE_CHAR_CLASS}）も入れさせない。画面では無害に見える本文に、日記のプロンプトへの指示を隠せないようにする。
 */
public record PostForm(
        @NotBlank(message = "内容を入力してください")
        @Size(max = Post.CONTENT_MAX_LENGTH, message = "{max} 文字以内で入力してください")
        @Pattern(regexp = "[^\r\n]*", message = "改行は入れられません")
        @Pattern(regexp = Post.NO_INVISIBLE_CHARS_REGEX, message = "見えない文字や制御文字は入れられません")
        String content) {}
