package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.PostPhoto;
import com.example.tsumory.repository.PostPhotoRepository;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    /** 1 日（日本時間）に投稿できるつぶやきの件数。 */
    public static final int DAILY_POST_LIMIT = 16;

    private final PostRepository postRepository;
    private final PostPhotoRepository postPhotoRepository;
    private final UserRepository userRepository;
    private final PostCategoryClassifier categoryClassifier;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** つぶやきの編集・削除を受け付ける日付（今日と昨日）なら {@code true}。日記を開ける日付は {@link DiaryService#isAvailableDate} で別に判定する。 */
    public boolean isEditableDate(LocalDate date) {
        var today = today();
        return date.equals(today) || date.equals(today.minusDays(1));
    }

    /** その日（日本時間）のつぶやきを、画面に出す形にして投稿時刻の古い順に返す。写真の有無も入れる（写真のデータは読まない）。 */
    public List<PostItem> findPosts(Long userId, LocalDate date) {
        var posts = findPostsByDate(userId, date);
        // つぶやきごとに問い合わせず（N+1 にしない）、写真のあるつぶやきの ID をまとめて取る
        var withPhoto = posts.isEmpty()
                ? Set.<Long>of()
                : new HashSet<>(postPhotoRepository.findPostIdsWithPhoto(
                        posts.stream().map(Post::getId).toList()));
        return posts.stream()
                .map(post -> PostItem.from(post, clock.getZone(), withPhoto.contains(post.getId())))
                .toList();
    }

    /** 任意の日付（日本時間）のつぶやきを、投稿時刻の古い順に返す。日付から時刻の範囲への変換はリポジトリが行う。 */
    public List<Post> findPostsByDate(Long userId, LocalDate date) {
        return postRepository.findByUserIdAndDate(userId, date, clock.getZone());
    }

    /**
     * つぶやきをキーワードとカテゴリで検索し、新しい順に最大 {@code limit} 件返す。キーワードとカテゴリのどちらかは指定する。
     *
     * <p>キーワードは {@code LIKE} 用にエスケープしてから渡す（日記の {@link DiaryService#searchDiaries} と同じく、エスケープ前のキーワードを受け取る）。
     *
     * @param keyword 前後の空白を取り除いたキーワード。なければ空文字
     * @param category 絞り込むカテゴリ。「すべて」なら {@code null}
     */
    public List<Post> searchPosts(Long userId, String keyword, PostCategory category, Limit limit) {
        if (keyword.isEmpty()) {
            return postRepository.searchByCategory(userId, category, limit);
        }
        var escaped = LikeKeyword.escape(keyword);
        return category == null
                ? postRepository.searchByKeyword(userId, escaped, limit)
                : postRepository.searchByKeywordAndCategory(userId, escaped, category, limit);
    }

    public long countPosts(Long userId, LocalDate date) {
        return postRepository.countByUserIdAndDate(userId, date, clock.getZone());
    }

    /**
     * その日（日本時間）のつぶやきが上限（{@link #DAILY_POST_LIMIT} 件）に達していれば {@code true}。画面で投稿フォームを無効にする判定と、
     * 投稿を断る判定（{@link #create}）の両方がこれを使い、2 つの判定がずれないようにする。
     */
    public boolean isPostLimitReached(Long userId, LocalDate date) {
        return countPosts(userId, date) >= DAILY_POST_LIMIT;
    }

    /** 編集モードで開くつぶやきを返す。自分の今日か昨日のつぶやきでなければ {@link ResourceNotFoundException}。 */
    public PostItem getEditablePost(Long userId, Long postId) {
        var post = findEditablePost(userId, postId);
        return PostItem.from(post, clock.getZone(), postPhotoRepository.existsById(post.getId()));
    }

    /**
     * 自分のつぶやきに添付した写真を返す。日付は問わない（日記表示から過去の写真も開ける）。ほかのユーザーのつぶやき・写真のないつぶやき・
     * 削除したつぶやきなら {@link ResourceNotFoundException}（どれも区別しない。CLAUDE.md の禁止事項「他人のデータ（IDOR）」）。
     */
    public PhotoContent findPhoto(Long userId, Long postId) {
        var photo = postPhotoRepository.findByPostIdAndUserId(postId, userId).orElseThrow(() -> {
            // 他人の写真へのアクセスの試行を含む（写真のないつぶやき・削除済みとは区別できない）
            log.warn("Photo not found for user: userId={}, postId={}", userId, postId);
            return new ResourceNotFoundException("PostPhoto", postId);
        });
        return new PhotoContent(photo.getContentType(), photo.getData());
    }

    /**
     * 今日のつぶやきとして投稿する。保存の前に AI でカテゴリを判定し、判定できたときだけ入れる（できなければ初期値の OTHER）。
     * 今日のつぶやきが {@link #DAILY_POST_LIMIT} 件に達していれば {@link DailyPostLimitExceededException}。
     *
     * <p>Claude API はトランザクションの外で呼び、DB を読む・保存するところだけを短いトランザクションにする（CLAUDE.md の「Spring・Lombok」）。
     * 写真は、つぶやきと同じトランザクションで保存する。どちらかが失敗したら、両方とも保存しない（phase3-spec.md の「6. 処理の詳細 > つぶやきの投稿」）。
     *
     * @param photo 添付する写真。なければ {@code null}
     * @return 保存したつぶやき（判定したカテゴリが入っている）
     * @throws InvalidPhotoException 写真を受け付けられないとき（{@link PhotoValidator#inspect}）。上限の確認と Claude API より前に確かめる
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Post create(Long userId, String rawContent, PhotoUpload photo) {
        var content = rawContent.strip();
        // 受け付けない写真なら、Claude API を呼ぶ前に断る（料金をかけないため）
        var photoType = photo == null ? null : inspectPhoto(userId, photo);
        var now = clock.instant();
        var today = LocalDate.ofInstant(now, clock.getZone());
        // 上限に達しているなら、Claude API を呼ぶ前に断る（料金をかけないため）。読むだけでも短いトランザクションで囲む（CLAUDE.md の
        // 「Spring・Lombok」）
        transactionTemplate.executeWithoutResult(status -> checkPostLimit(userId, today));
        var post = new Post(userRepository.getReferenceById(userId), content, now);
        categoryClassifier.classify(content).ifPresent(post::assignCategory);
        var saved = transactionTemplate.execute(status -> {
            // 判定を待つ間に、別のタブなどから投稿されていないか確かめ直す
            checkPostLimit(userId, today);
            var savedPost = postRepository.save(post);
            if (photo != null) {
                postPhotoRepository.save(new PostPhoto(savedPost, photoType.contentType(), photo.data(), now));
            }
            return savedPost;
        });
        // 写真は有無と大きさだけを出す（中身・ファイル名は出さない）
        log.info(
                "Post created: userId={}, postId={}, contentLength={}, photoBytes={}",
                userId,
                saved.getId(),
                content.length(),
                photo == null ? 0 : photo.size());
        return saved;
    }

    /**
     * 本文とカテゴリを直す。送られたカテゴリが今のカテゴリと違えば、ユーザーが手で直したものとして入れ、印を立てる。
     *
     * <p>本文が変わったときは AI でカテゴリを判定し直し、判定できたときだけ入れる（できなければ前のカテゴリのまま）。ただし、ユーザーが手で直したカテゴリ
     * （以前に直したものも、このリクエストで直したものも）は判定し直さない。
     *
     * <p>{@link #create} と同じく、Claude API はトランザクションの外で呼び、DB を読む・保存するところだけを短いトランザクションにする。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void edit(Long userId, Long postId, String content, PostCategory category) {
        var stripped = content.strip();
        // create と同じく、読むだけでも短いトランザクションで囲む
        var current = transactionTemplate.execute(status -> findEditablePost(userId, postId));
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
        var post = findOwnPost(userId, postId);
        post.assignCategory(category);
        post.markCategorySetByUser();
        // どのカテゴリにしたかは出さない（CLAUDE.md の禁止事項「ログ」）
        log.info("Post category set by user: userId={}, postId={}", userId, postId);
    }

    /**
     * 自分の今日か昨日のつぶやきを削除する。それ以外（他人のもの、削除済み、一昨日より前）は {@link ResourceNotFoundException}。
     * 他人のつぶやきと存在しないつぶやきは区別しない（CLAUDE.md の禁止事項「他人のデータ（IDOR）」）。
     */
    @Transactional
    public void delete(Long userId, Long postId) {
        postRepository.delete(findEditablePost(userId, postId));
        log.info("Post deleted: userId={}, postId={}", userId, postId);
    }

    /** 写真を確かめて形式を返す。受け付けない写真なら、理由をログに残して {@link InvalidPhotoException} をそのまま投げる。 */
    private static PhotoType inspectPhoto(Long userId, PhotoUpload photo) {
        try {
            return PhotoValidator.inspect(photo.data());
        } catch (InvalidPhotoException e) {
            log.warn("Photo rejected: userId={}, reason={}, photoBytes={}", userId, e.getReason(), photo.size());
            throw e;
        }
    }

    private void checkPostLimit(Long userId, LocalDate today) {
        if (isPostLimitReached(userId, today)) {
            log.warn("Post limit reached: userId={}, date={}, limit={}", userId, today, DAILY_POST_LIMIT);
            throw new DailyPostLimitExceededException(userId, DAILY_POST_LIMIT);
        }
    }

    /** 自分のつぶやきを返す。他人のつぶやきや削除済みなら {@link ResourceNotFoundException}（2 つは区別しない）。 */
    private Post findOwnPost(Long userId, Long postId) {
        return postRepository.findByIdAndUserId(postId, userId).orElseThrow(() -> {
            // 他人のつぶやきへのアクセスの試行を含む（削除済みの ID と区別はできない）
            log.warn("Post not found for user: userId={}, postId={}", userId, postId);
            return new ResourceNotFoundException("Post", postId);
        });
    }

    /** 自分のつぶやきのうち、編集・削除できる日付（今日と昨日）のものを返す。それ以外は {@link ResourceNotFoundException}。 */
    private Post findEditablePost(Long userId, Long postId) {
        var post = findOwnPost(userId, postId);
        var postedDate = LocalDate.ofInstant(post.getPostedAt(), clock.getZone());
        if (!isEditableDate(postedDate)) {
            // 0 時を過ぎて、開いたままの画面から一昨日の分を直そうとした場合など
            log.warn("Post not editable: userId={}, postId={}, postedDate={}", userId, postId, postedDate);
            throw new ResourceNotFoundException("Post", postId);
        }
        return post;
    }
}
