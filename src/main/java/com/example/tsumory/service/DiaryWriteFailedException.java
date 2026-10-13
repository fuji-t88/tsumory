package com.example.tsumory.service;

/** {@link DiaryWriter} がエラーやタイムアウトで日記を書けなかったときに投げる。 */
public class DiaryWriteFailedException extends RuntimeException {

    public DiaryWriteFailedException() {
        super("Diary writing failed");
    }
}
