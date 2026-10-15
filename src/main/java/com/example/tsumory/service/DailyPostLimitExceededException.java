package com.example.tsumory.service;

/**
 * 今日のつぶやきが 1 日の上限（{@link PostService#DAILY_POST_LIMIT}）に達していて、投稿できないときに投げる。
 *
 * <p>メッセージは開発者向け（ログから状況を再現しやすくするため、ユーザー ID と上限値を含める）。ユーザーに見せるメッセージは controller が持つ。
 */
public class DailyPostLimitExceededException extends RuntimeException {

    public DailyPostLimitExceededException(Long userId, int limit) {
        super("Daily post limit exceeded: userId=" + userId + ", limit=" + limit);
    }
}
