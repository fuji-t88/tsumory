package com.example.tsumory.service;

/** 今日のつぶやきが 0 件で、日記を書けないときに投げる。 */
public class NoPostsException extends RuntimeException {

    public NoPostsException() {
        super("no posts to generate diary from");
    }
}
