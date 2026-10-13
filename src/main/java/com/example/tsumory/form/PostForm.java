package com.example.tsumory.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostForm(
        @NotBlank(message = "内容を入力してください") @Size(max = 100, message = "100 文字以内で入力してください")
        String content) {}
