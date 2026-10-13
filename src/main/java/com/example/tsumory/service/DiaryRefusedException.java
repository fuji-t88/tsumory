package com.example.tsumory.service;

/** {@link DiaryWriter} が日記を書くことを断ったときに投げる。 */
public class DiaryRefusedException extends RuntimeException {

    public DiaryRefusedException() {
        super("Diary writing refused");
    }
}
