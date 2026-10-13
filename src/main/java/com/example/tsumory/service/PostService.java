package com.example.tsumory.service;

import com.example.tsumory.domain.Post;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private final PostRepository postRepository;
    private final UserRepository userRepository;
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

    public long countPosts(Long userId, LocalDate date) {
        return postRepository.countInRange(userId, startOf(date), startOf(date.plusDays(1)));
    }

    /** 編集モードで開くつぶやきを返す。自分の今日か昨日のつぶやきでなければ {@link PostNotFoundException}。 */
    public PostItem getEditablePost(Long userId, Long postId) {
        return PostItem.from(findEditablePost(userId, postId), ZONE);
    }

    /** 今日のつぶやきとして投稿する。 */
    @Transactional
    public void post(Long userId, String content) {
        var stripped = content.strip();
        var post = postRepository.save(new Post(userRepository.getReferenceById(userId), stripped, clock.instant()));
        log.info("Post created: userId={}, postId={}, contentLength={}", userId, post.getId(), stripped.length());
    }

    @Transactional
    public void edit(Long userId, Long postId, String content) {
        var stripped = content.strip();
        findEditablePost(userId, postId).edit(stripped, clock.instant());
        log.info("Post edited: userId={}, postId={}, contentLength={}", userId, postId, stripped.length());
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        postRepository.delete(findEditablePost(userId, postId));
        log.info("Post deleted: userId={}, postId={}", userId, postId);
    }

    private Post findEditablePost(Long userId, Long postId) {
        var post = postRepository.findByIdAndUserId(postId, userId).orElseThrow(() -> {
            // 他人のつぶやきへのアクセスの試行を含む（削除済みの ID と区別はできない）
            log.warn("Post not found for user: userId={}, postId={}", userId, postId);
            return new PostNotFoundException(postId);
        });
        var postedDate = LocalDate.ofInstant(post.getPostedAt(), ZONE);
        if (!isEditableDate(postedDate)) {
            // 0 時を過ぎて、開いたままの画面から一昨日の分を直そうとした場合など
            log.info("Post not editable: userId={}, postId={}, postedDate={}", userId, postId, postedDate);
            throw new PostNotFoundException(postId);
        }
        return post;
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
