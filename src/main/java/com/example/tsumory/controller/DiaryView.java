package com.example.tsumory.controller;

/** 画面に出す日記。生成時刻は表示用の文字列（{@code HH:mm}、日本時間）。 */
public record DiaryView(String content, String generatedTime) {}
