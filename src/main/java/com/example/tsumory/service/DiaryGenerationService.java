package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryGenerationService {

    private final PostService postService;
    private final DiaryService diaryService;
    private final DiaryWriter diaryWriter;
    private final Clock clock;

    /**
     * その日のつぶやきから日記を書き、その日の日記として保存する（遡り生成。今日の分もこれで書く）。
     *
     * <p>次のときは AI を呼ばずに例外を投げる。
     *
     * <ul>
     *   <li>今日より後の日付：{@link InvalidDiaryDateException}（controller がメッセージを付けてホームへ戻す）
     *   <li>登録日より前の日付：{@link DiaryNotFoundException}（404。画面からは送れず、URL を書き換えたときだけ起きる）
     *   <li>その日のつぶやきが 0 件：{@link NoPostsException}
     * </ul>
     *
     * <p>AI の呼び出しが失敗したら {@link DiaryGenerationException} をそのまま投げ、日記は変えない。
     *
     * <p>同じ日の日記を同時に生成して保存がぶつかったときは、先に保存された日記の上書きとして保存し直す（後から保存した方の本文が残る）。
     */
    // Claude API 呼び出しが長秒数になるため、
    // 外側でトランザクションを保持しない。
    // findPostsByDate / saveDiary はそれぞれ
    // 独立したトランザクションを張る。
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Diary generateForDate(Long userId, LocalDate date) {
        // 業務上の失敗は WARN で残す（CLAUDE.md の「ログ」）。AI は呼ばない
        if (date.isAfter(LocalDate.now(clock))) {
            log.warn("Diary date is in the future: userId={}, date={}", userId, date);
            throw new InvalidDiaryDateException(date);
        }
        if (!diaryService.isAvailableDate(userId, date)) {
            log.warn("Diary date not available: userId={}, date={}", userId, date);
            throw new DiaryNotFoundException(date);
        }
        var posts = postService.findPostsByDate(userId, date);
        if (posts.isEmpty()) {
            log.warn("No posts to generate diary: userId={}, date={}", userId, date);
            throw new NoPostsException();
        }
        var generated = diaryWriter.write(date, posts);
        try {
            return diaryService.saveDiary(userId, date, generated);
        } catch (DataIntegrityViolationException e) {
            // 別のタブなどから同じ日の日記を同時に生成し、AI の応答を待つ間に先に INSERT された（一意制約の違反）。
            // saveDiary のトランザクションはロールバック済みなので、もう一度呼べば既存の日記の上書きになる。
            // 2 回目も失敗したら、一意制約以外の原因なのでそのまま投げる（500）
            log.warn(
                    "Diary save conflicted, retrying as update: userId={}, date={}, exception={}",
                    userId,
                    date,
                    e.getClass().getSimpleName());
            return diaryService.saveDiary(userId, date, generated);
        }
    }
}
