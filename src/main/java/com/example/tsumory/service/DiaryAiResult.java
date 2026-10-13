package com.example.tsumory.service;

/** Claude API で日記を書いた結果。 */
public sealed interface DiaryAiResult {

    record Written(String content) implements DiaryAiResult {}

    record Refused() implements DiaryAiResult {}

    record Failed() implements DiaryAiResult {}
}
