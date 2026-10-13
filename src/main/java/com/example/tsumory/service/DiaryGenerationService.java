package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 日記を書く流れを組み立てる。つぶやきを集め、0 件なら書かず、{@link DiaryWriter} に書かせ、{@link DiaryService} に保存させる。
 *
 * <p>AI の呼び出しは DB のトランザクションの外で行うため、このクラスにはトランザクションを付けない（保存だけが
 * {@link DiaryService#save} のトランザクションで動く）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryGenerationService {

    private final PostService postService;
    private final DiaryService diaryService;
    private final DiaryWriter diaryWriter;

    /** 生成中の「ユーザー ID + 日付」。同じユーザー・同じ日付の生成を同時に 1 つだけにする。 */
    private final Set<GenerationKey> inProgress = ConcurrentHashMap.newKeySet();

    private record GenerationKey(Long userId, LocalDate date) {}

    /** その日のつぶやきから日記を書き、なければ保存、あれば上書きする。今日と昨日以外の日付なら {@link DiaryNotFoundException}。 */
    public DiaryGenerationResult generate(Long userId, LocalDate date) {
        if (!postService.isEditableDate(date)) {
            log.info("Diary date not available: userId={}, date={}", userId, date);
            throw new DiaryNotFoundException(date);
        }
        var posts = postService.findPosts(userId, date);
        if (posts.isEmpty()) {
            log.info("Diary generation skipped, no posts: userId={}, date={}", userId, date);
            return new DiaryGenerationResult.NoPosts();
        }

        var key = new GenerationKey(userId, date);
        if (!inProgress.add(key)) {
            log.info("Diary generation skipped, already in progress: userId={}, date={}", userId, date);
            return new DiaryGenerationResult.InProgress();
        }
        log.info("Diary generation started: userId={}, date={}, posts={}", userId, date, posts.size());
        var startedAt = System.nanoTime();
        try {
            // DiaryWriter は拒否・失敗を例外で知らせるので、ここで DiaryGenerationResult に変えて controller に返す
            String content;
            try {
                content = diaryWriter.write(
                        date, posts.stream().map(PostItem::content).toList());
            } catch (DiaryRefusedException e) {
                log.warn(
                        "Diary generation refused: userId={}, date={}, elapsedMs={}",
                        userId,
                        date,
                        elapsedMs(startedAt));
                return new DiaryGenerationResult.Refused();
            } catch (DiaryWriteFailedException e) {
                log.warn(
                        "Diary generation failed: userId={}, date={}, elapsedMs={}",
                        userId,
                        date,
                        elapsedMs(startedAt));
                return new DiaryGenerationResult.Failed();
            }
            var diary = save(userId, date, content);
            log.info(
                    "Diary generated: userId={}, date={}, diaryId={}, contentLength={}, elapsedMs={}",
                    userId,
                    date,
                    diary.getId(),
                    content.length(),
                    elapsedMs(startedAt));
            return new DiaryGenerationResult.Generated(diary);
        } finally {
            inProgress.remove(key);
        }
    }

    private Diary save(Long userId, LocalDate date, String content) {
        try {
            return diaryService.save(userId, date, content);
        } catch (DataIntegrityViolationException e) {
            // 同じ日記を同時に INSERT して一意制約に当たったときは、新しいトランザクションで先に入った行を上書きする
            log.warn("Diary insert hit unique constraint, retrying as update: userId={}, date={}", userId, date);
            return diaryService.save(userId, date, content);
        }
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
