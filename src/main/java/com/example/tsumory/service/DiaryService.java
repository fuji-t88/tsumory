package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 日記の DB アクセス（取得と保存）。日記を書く流れの組み立ては {@link DiaryGenerationService} が受け持つ。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /** ユーザーの登録日（日本時間）。日記を開ける最初の日。 */
    @Transactional(readOnly = true)
    public LocalDate registeredDate(Long userId) {
        var user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return LocalDate.ofInstant(user.getCreatedAt(), PostService.ZONE);
    }

    /** 日記を開ける日付（登録日から今日まで）なら {@code true}。日記表示と日記一覧のカレンダーで使う。 */
    @Transactional(readOnly = true)
    public boolean isAvailableDate(Long userId, LocalDate date) {
        return !date.isBefore(registeredDate(userId)) && !date.isAfter(LocalDate.now(clock));
    }

    /** その日の日記を返す。開ける日付（登録日から今日まで）でなければ {@link DiaryNotFoundException}。 */
    @Transactional(readOnly = true)
    public Optional<Diary> findByDate(Long userId, LocalDate date) {
        if (!isAvailableDate(userId, date)) {
            log.info("Diary date not available: userId={}, date={}", userId, date);
            throw new DiaryNotFoundException(date);
        }
        return diaryRepository.findByUserIdAndDiaryDate(userId, date);
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
     * 日記を新しい順に返す。キーワードが空（{@code null} や空白だけ）なら絞り込みのない一覧、あれば本文にキーワードを含む日記だけ。
     *
     * <p>一覧と検索で適したクエリが違うので、ここで振り分ける。{@code Pageable} は controller（または呼び出し側の service）が組み立てる。
     */
    @Transactional(readOnly = true)
    public Page<Diary> searchDiaries(Long userId, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return diaryRepository.findByUserIdOrderByDiaryDateDesc(userId, pageable);
        }
        return diaryRepository.searchByContent(userId, escapeForLike(keyword), pageable);
    }

    // ESCAPE 文字 `!` 自身を先に二重化し、その後で
    // LIKE のメタ文字 `%` `_` をエスケープする。
    // 順序が逆だと、後から導入した `!` を再エスケープしてしまう。
    private static String escapeForLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** その日の日記を、なければ INSERT、あれば上書きする。生成時刻は今（過去の日の日記を書いても、書いた時刻が入る）。 */
    @Transactional
    public Diary saveDiary(Long userId, LocalDate date, String content) {
        var now = clock.instant();
        var existing = diaryRepository.findByUserIdAndDiaryDate(userId, date);
        if (existing.isPresent()) {
            var diary = existing.get();
            diary.updateContent(content, now);
            log.info("Diary updated: userId={}, date={}, diaryId={}", userId, date, diary.getId());
            return diary;
        }
        // ID は DB の自動採番（IDENTITY）なので、save の時点で INSERT され、ログに出す ID が入る
        var diary = diaryRepository.save(new Diary(userRepository.getReferenceById(userId), date, content, now));
        log.info("Diary created: userId={}, date={}, diaryId={}", userId, date, diary.getId());
        return diary;
    }
}
