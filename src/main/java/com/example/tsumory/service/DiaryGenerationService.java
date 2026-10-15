package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
     */
    // Claude API 呼び出しが長秒数になるため、
    // 外側でトランザクションを保持しない。
    // findPostsByDate / saveDiary はそれぞれ
    // 独立したトランザクションを張る。
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Diary generateForDate(Long userId, LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new InvalidDiaryDateException(date);
        }
        if (!diaryService.isAvailableDate(userId, date)) {
            throw new DiaryNotFoundException(date);
        }
        var posts = postService.findPostsByDate(userId, date);
        if (posts.isEmpty()) {
            throw new NoPostsException();
        }
        var generated = diaryWriter.write(date, posts);
        return diaryService.saveDiary(userId, date, generated);
    }
}
