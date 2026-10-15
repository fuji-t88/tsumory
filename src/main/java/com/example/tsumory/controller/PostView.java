package com.example.tsumory.controller;

import com.example.tsumory.domain.PostCategory;

/** 画面に出すつぶやき 1 件。投稿時刻は表示用の文字列（{@code HH:mm}、日本時間）。 */
public record PostView(Long id, String content, String time, boolean edited, PostCategory category) {}
