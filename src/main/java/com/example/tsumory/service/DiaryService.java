package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 日記の DB アクセス（取得と保存）。日記を書く流れの組み立ては {@link DiaryGenerationService} が受け持つ。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final Clock clock;

    /** その日の日記を返す。今日と昨日以外の日付なら {@link DiaryNotFoundException}。 */
    @Transactional(readOnly = true)
    public Optional<DiaryItem> findDiary(Long userId, LocalDate date) {
        if (!postService.isEditableDate(date)) {
            log.info("Diary date not available: userId={}, date={}", userId, date);
            throw new DiaryNotFoundException(date);
        }
        return diaryRepository
                .findByUserIdAndDiaryDate(userId, date)
                .map(diary -> DiaryItem.from(diary, PostService.ZONE));
    }

    /**
     * 今日のつぶやき画面の「日記あり」の表示用に、その日の日記を返す。
     *
     * <p>{@link #findDiary} と違い、日付が今日・昨日かは確かめない。呼び出し側が 1 回だけ求めた「今日」から今日と昨日を渡す前提。
     * ここで「今日」を求め直すと、0 時をまたいだ瞬間に昨日が一昨日の扱いになり、画面全体が 404 になるため。
     */
    @Transactional(readOnly = true)
    public Optional<DiaryItem> findDiaryForSummary(Long userId, LocalDate date) {
        return diaryRepository
                .findByUserIdAndDiaryDate(userId, date)
                .map(diary -> DiaryItem.from(diary, PostService.ZONE));
    }

    /**
     * その日の日記を、なければ INSERT、あれば上書きする。
     *
     * <p>同じ日記を同時に INSERT すると一意制約違反（{@code DataIntegrityViolationException}）になる。そのときはこのトランザクションを
     * ロールバックして例外を投げるので、呼び出し側がもう一度呼べば、先に入った行を上書きする。
     */
    @Transactional
    public Diary save(Long userId, LocalDate date, String content) {
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
}
