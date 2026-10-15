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

    // Claude API 呼び出しが長秒数になるため、
    // 外側でトランザクションを保持しない。
    // findTodayPosts / saveDiary はそれぞれ
    // 独立したトランザクションを張る。
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Diary generateToday(Long userId) {
        var posts = postService.findTodayPosts(userId);
        if (posts.isEmpty()) {
            throw new NoPostsException();
        }
        var today = LocalDate.now(clock);
        var generated = diaryWriter.write(today, posts);
        return diaryService.saveDiary(userId, generated);
    }
}
