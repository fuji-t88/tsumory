package com.example.tsumory.form;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** 日記生成フォームの隠しフィールド。どの日の日記を書くか（{@code yyyy-MM-dd}）。 */
public record DiaryGenerateForm(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date) {}
