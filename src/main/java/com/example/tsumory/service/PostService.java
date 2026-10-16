package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
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

    /** つぶやきの編集・削除を受け付ける日付（今日と昨日）なら {@code true}。日記を開ける日付は {@link DiaryService#isAvailableDate} で別に判定する。 */
    public boolean isEditableDate(LocalDate date) {
        var today = today();
        return date.equals(today) || date.equals(today.minusDays(1));
    }

    /** その日（日本時間）のつぶやきを、画面に出す形にして投稿時刻の古い順に返す。 */
    public List<PostItem> findPosts(Long userId, LocalDate date) {
        return findPostsByDate(userId, date).stream()
                .map(post -> PostItem.from(post, ZONE))
                .toList();
    }

    /** 任意の日付（日本時間）のつぶやきを、投稿時刻の古い順に返す。日付から時刻の範囲への変換はリポジトリが行う。 */
    public List<Post> findPostsByDate(Long userId, LocalDate date) {
        return postRepository.findByUserIdAndDate(userId, date, clock.getZone());
    }

    /** 今日（日本時間）のつぶやきを投稿時刻の古い順に返す。日記を書く材料にする。 */
    public List<Post> findTodayPosts(Long userId) {
        return findPostsByDate(userId, LocalDate.now(clock));
    }

    public long countPosts(Long userId, LocalDate date) {
        return postRepository.countByUserIdAndDate(userId, date, clock.getZone());
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
     *
     * @return 保存したつぶやき（判定したカテゴリが入っている）
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Post create(Long userId, String rawContent) {
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
        return saved;
    }

    /**
     * 本文とカテゴリを直す。送られたカテゴリが今のカテゴリと違えば、ユーザーが手で直したものとして入れ、印を立てる。
     *
     * <p>本文が変わったときは AI でカテゴリを判定し直し、判定できたときだけ入れる（できなければ前のカテゴリのまま）。ただし、ユーザーが手で直したカテゴリ
     * （以前に直したものも、このリクエストで直したものも）は判定し直さない。
     *
     * <p>{@link #create} と同じく、Claude API はトランザクションの外で呼び、保存だけをトランザクションにする。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void edit(Long userId, Long postId, String content, PostCategory category) {
        var stripped = content.strip();
        var current = findEditablePost(userId, postId);
        var contentChanged = !stripped.equals(current.getContent());
        // 今のカテゴリと違う値が送られたら、ユーザーが手で直したとみなす
        var categoryChangedByUser = category != current.getCategory();
        var shouldClassify = contentChanged && !categoryChangedByUser && !current.isCategorySetByUser();
        var classified = shouldClassify ? categoryClassifier.classify(stripped) : Optional.<PostCategory>empty();
        var now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            // 判定を待つ間に削除・手での修正がされていないか、トランザクションの中で読み直して確かめる
            var post = findEditablePost(userId, postId);
            // 本文が変わらないなら updated_at も変えない（カテゴリだけを直しても「編集済み」にしない）
            if (contentChanged) {
                post.edit(stripped, now);
            }
            if (categoryChangedByUser) {
                post.assignCategory(category);
                post.markCategorySetByUser();
            } else if (!post.isCategorySetByUser()) {
                classified.ifPresent(post::assignCategory);
            }
        });
        // どのカテゴリにしたかは出さない（CLAUDE.md の禁止事項「ログ」）
        log.info(
                "Post edited: userId={}, postId={}, contentLength={}, classified={}, categorySetByUser={}",
                userId,
                postId,
                stripped.length(),
                shouldClassify,
                categoryChangedByUser);
    }

    /**
     * カテゴリだけを手で直す（日記表示のつぶやきの一覧から）。印を立て、以後は AI に上書きさせない。本文と更新時刻は変えない（「編集済み」にしない）。
     *
     * <p>本文の編集と違い、日付は問わない（自分のつぶやきなら、昨日より前のものも直せる）。他人のつぶやきなら {@link ResourceNotFoundException}。
     */
    @Transactional
    public void updateCategory(Long userId, Long postId, PostCategory category) {
        var post = postRepository.findByIdAndUserId(postId, userId).orElseThrow(() -> {
            log.warn("Post not found for user: userId={}, postId={}", userId, postId);
            return new ResourceNotFoundException("Post", postId);
        });
        post.assignCategory(category);
        post.markCategorySetByUser();
        // どのカテゴリにしたかは出さない（CLAUDE.md の禁止事項「ログ」）
        log.info("Post category set by user: userId={}, postId={}", userId, postId);
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        postRepository.delete(findEditablePost(userId, postId));
        log.info("Post deleted: userId={}, postId={}", userId, postId);
    }

    private void checkPostLimit(Long userId, LocalDate today) {
        if (countPosts(userId, today) >= DAILY_POST_LIMIT) {
            log.warn("Post limit reached: userId={}, date={}, limit={}", userId, today, DAILY_POST_LIMIT);
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
            log.warn("Post not editable: userId={}, postId={}, postedDate={}", userId, postId, postedDate);
            throw new ResourceNotFoundException("Post", postId);
        }
        return post;
    }
}
