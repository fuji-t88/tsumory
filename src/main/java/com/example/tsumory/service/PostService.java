package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    /** 1 日（日本時間）に投稿できるつぶやきの件数。 */
    public static final int DAILY_POST_LIMIT = 16;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostCategoryClassifier categoryClassifier;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    /** 編集・削除、日記の表示と生成を受け付ける日付（今日と昨日）なら {@code true}。 */
    public boolean isEditableDate(LocalDate date) {
        var today = today();
        return date.equals(today) || date.equals(today.minusDays(1));
    }

    /** その日（日本時間）のつぶやきを投稿時刻の古い順に返す。 */
    public List<PostItem> findPosts(Long userId, LocalDate date) {
        return postRepository.findInRange(userId, startOf(date), startOf(date.plusDays(1))).stream()
                .map(post -> PostItem.from(post, ZONE))
                .toList();
    }

    /** 今日（日本時間）のつぶやきを投稿時刻の古い順に返す。日記を書く材料にする。 */
    public List<Post> findTodayPosts(Long userId) {
        var today = today();
        return postRepository.findInRange(userId, startOf(today), startOf(today.plusDays(1)));
    }

    public long countPosts(Long userId, LocalDate date) {
        return postRepository.countInRange(userId, startOf(date), startOf(date.plusDays(1)));
    }

    /** 編集モードで開くつぶやきを返す。自分の今日か昨日のつぶやきでなければ {@link ResourceNotFoundException}。 */
    public PostItem getEditablePost(Long userId, Long postId) {
        return PostItem.from(findEditablePost(userId, postId), ZONE);
    }

    /**
     * 今日のつぶやきとして投稿する。保存の前に AI でカテゴリを判定し、判定できたときだけ入れる（できなければ初期値の OTHER）。
     * 今日のつぶやきが {@link #DAILY_POST_LIMIT} 件に達していれば {@link DailyPostLimitExceededException}。
     *
     * <p>Claude API の応答を待つ間に DB 接続を握らないよう、このメソッドはトランザクションの外で動かし、保存だけをトランザクションにする。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void create(Long userId, String rawContent) {
        var content = rawContent.strip();
        var now = clock.instant();
        var today = LocalDate.ofInstant(now, ZONE);
        // 上限に達しているなら、Claude API を呼ぶ前に断る（料金をかけないため）
        checkPostLimit(userId, today);
        var post = new Post(userRepository.getReferenceById(userId), content, now);
        categoryClassifier.classify(content).ifPresent(post::assignCategory);
        var saved = transactionTemplate.execute(status -> {
            // 判定を待つ間に、別のタブなどから投稿されていないか確かめ直す
            checkPostLimit(userId, today);
            return postRepository.save(post);
        });
        log.info("つぶやき投稿: userId={}, postId={}, contentLength={}", userId, saved.getId(), content.length());
    }

    /**
     * 本文を直す。本文が変わったときだけ AI でカテゴリを判定し直し、判定できたときだけ入れる（できなければ前のカテゴリのまま）。 ユーザーが手で直したカテゴリは判定し直さない。
     *
     * <p>{@link #create} と同じく、Claude API はトランザクションの外で呼び、保存だけをトランザクションにする。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void edit(Long userId, Long postId, String content) {
        var stripped = content.strip();
        var current = findEditablePost(userId, postId);
        var shouldClassify = !stripped.equals(current.getContent()) && !current.isCategorySetByUser();
        var category = shouldClassify ? categoryClassifier.classify(stripped) : Optional.<PostCategory>empty();
        var now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            // 判定を待つ間に削除・手での修正がされていないか、トランザクションの中で読み直して確かめる
            var post = findEditablePost(userId, postId);
            post.edit(stripped, now);
            if (!post.isCategorySetByUser()) {
                category.ifPresent(post::assignCategory);
            }
        });
        log.info(
                "Post edited: userId={}, postId={}, contentLength={}, classified={}",
                userId,
                postId,
                stripped.length(),
                shouldClassify);
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        postRepository.delete(findEditablePost(userId, postId));
        log.info("Post deleted: userId={}, postId={}", userId, postId);
    }

    private void checkPostLimit(Long userId, LocalDate today) {
        if (countPosts(userId, today) >= DAILY_POST_LIMIT) {
            log.info("Post limit reached: userId={}, date={}, limit={}", userId, today, DAILY_POST_LIMIT);
            throw new DailyPostLimitExceededException(userId, DAILY_POST_LIMIT);
        }
    }

    private Post findEditablePost(Long userId, Long postId) {
        var post = postRepository.findByIdAndUserId(postId, userId).orElseThrow(() -> {
            // 他人のつぶやきへのアクセスの試行を含む（削除済みの ID と区別はできない）
            log.warn("Post not found for user: userId={}, postId={}", userId, postId);
            return new ResourceNotFoundException("Post", postId);
        });
        var postedDate = LocalDate.ofInstant(post.getPostedAt(), ZONE);
        if (!isEditableDate(postedDate)) {
            // 0 時を過ぎて、開いたままの画面から一昨日の分を直そうとした場合など
            log.info("Post not editable: userId={}, postId={}, postedDate={}", userId, postId, postedDate);
            throw new ResourceNotFoundException("Post", postId);
        }
        return post;
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
