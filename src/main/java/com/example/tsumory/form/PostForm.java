package com.example.tsumory.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 投稿フォーム。
 *
 * <p>本文に改行を入れさせない。日記のプロンプトでは 1 行に 1 つぶやきを並べるので、改行で偽の行を紛れ込ませられないようにする（{@code Post} でも同じ値を拒否する）。
 */
public record PostForm(
        @NotBlank(message = "内容を入力してください")
        @Size(max = 100, message = "100 文字以内で入力してください")
        @Pattern(regexp = "[^\r\n]*", message = "改行は入れられません")
        String content) {}
