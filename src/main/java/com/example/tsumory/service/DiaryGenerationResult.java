package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;

/** 日記生成・再生成の結果。controller で switch して分岐する。 */
public sealed interface DiaryGenerationResult {

    record Generated(Diary diary) implements DiaryGenerationResult {}

    record NoPosts() implements DiaryGenerationResult {}

    record InProgress() implements DiaryGenerationResult {}

    record Refused() implements DiaryGenerationResult {}

    record Failed() implements DiaryGenerationResult {}
}
