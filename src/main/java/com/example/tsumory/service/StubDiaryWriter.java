package com.example.tsumory.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Claude API を呼ばない {@link DiaryWriter} の仮実装。Claude につなぐ前の動作確認に使う。
 *
 * <p>つぶやきを投稿順に並べただけの仮の日記を返す。待ち時間（{@code tsumory.stub-diary-writer.delay}）と結果
 * （{@code tsumory.stub-diary-writer.result}：{@code written}・{@code refused}・{@code failed}）を設定で変えられるので、
 * ローディング表示や失敗時のメッセージも確かめられる。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "tsumory.diary-writer", havingValue = "stub")
public class StubDiaryWriter implements DiaryWriter {

    static final String HEADER = "（仮の日記です。Claude API にはつないでいません）";

    private final Duration delay;
    private final String result;

    public StubDiaryWriter(
            @Value("${tsumory.stub-diary-writer.delay:0s}") Duration delay,
            @Value("${tsumory.stub-diary-writer.result:written}") String result) {
        this.delay = delay;
        this.result = result;
        log.info("Using stub diary writer (Claude API is not called): delay={}, result={}", delay, result);
    }

    @Override
    public String write(LocalDate date, List<String> contents) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DiaryWriteFailedException();
        }
        log.info("Stub diary written: date={}, contents={}, result={}", date, contents.size(), result);
        return switch (result) {
            case "refused" -> throw new DiaryRefusedException();
            case "failed" -> throw new DiaryWriteFailedException();
            default -> HEADER + "\n\n" + String.join("\n", contents);
        };
    }
}
