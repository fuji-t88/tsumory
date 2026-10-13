package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final DiaryAiService diaryAiService;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    /** 生成中の「ユーザー ID + 日付」。同じユーザー・同じ日付の生成を同時に 1 つだけにする。 */
    private final Set<GenerationKey> inProgress = ConcurrentHashMap.newKeySet();

    private record GenerationKey(Long userId, LocalDate date) {}

    /** その日の日記を返す。今日と昨日以外の日付なら {@link DiaryNotFoundException}。 */
    @Transactional(readOnly = true)
    public Optional<DiaryItem> findDiary(Long userId, LocalDate date) {
        requireAvailableDate(userId, date);
        return diaryRepository
                .findByUserIdAndDiaryDate(userId, date)
                .map(diary -> DiaryItem.from(diary, PostService.ZONE));
    }

    /**
     * その日のつぶやきから日記を書き、なければ保存、あれば上書きする。
     *
     * <p>Claude API は DB のトランザクションの外で呼ぶため、このメソッドにはトランザクションを付けない。
     */
    public DiaryGenerationResult generate(Long userId, LocalDate date) {
        requireAvailableDate(userId, date);
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
            return switch (diaryAiService.write(date, posts)) {
                case DiaryAiResult.Written(var content) -> {
                    var diary = save(userId, date, content);
                    log.info(
                            "Diary generated: userId={}, date={}, diaryId={}, contentLength={}, elapsedMs={}",
                            userId,
                            date,
                            diary.getId(),
                            content.length(),
                            elapsedMs(startedAt));
                    yield new DiaryGenerationResult.Generated(diary);
                }
                case DiaryAiResult.Refused() -> {
                    log.warn(
                            "Diary generation refused: userId={}, date={}, elapsedMs={}",
                            userId,
                            date,
                            elapsedMs(startedAt));
                    yield new DiaryGenerationResult.Refused();
                }
                case DiaryAiResult.Failed() -> {
                    log.warn(
                            "Diary generation failed: userId={}, date={}, elapsedMs={}",
                            userId,
                            date,
                            elapsedMs(startedAt));
                    yield new DiaryGenerationResult.Failed();
                }
            };
        } finally {
            inProgress.remove(key);
        }
    }

    private void requireAvailableDate(Long userId, LocalDate date) {
        if (!postService.isEditableDate(date)) {
            log.info("Diary date not available: userId={}, date={}", userId, date);
            throw new DiaryNotFoundException(date);
        }
    }

    private Diary save(Long userId, LocalDate date, String content) {
        try {
            return transactionOperations.execute(status -> upsert(userId, date, content));
        } catch (DataIntegrityViolationException e) {
            // 同じ日記を同時に INSERT して一意制約に当たったときは、先に入った行を上書きする
            log.warn("Diary insert hit unique constraint, retrying as update: userId={}, date={}", userId, date);
            return transactionOperations.execute(status -> upsert(userId, date, content));
        }
    }

    private Diary upsert(Long userId, LocalDate date, String content) {
        var now = clock.instant();
        var existing = diaryRepository.findByUserIdAndDiaryDate(userId, date);
        if (existing.isPresent()) {
            var diary = existing.get();
            diary.updateContent(content, now);
            log.info("Diary updated: userId={}, date={}, diaryId={}", userId, date, diary.getId());
            return diary;
        }
        var diary =
                diaryRepository.saveAndFlush(new Diary(userRepository.getReferenceById(userId), date, content, now));
        log.info("Diary created: userId={}, date={}, diaryId={}", userId, date, diary.getId());
        return diary;
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
