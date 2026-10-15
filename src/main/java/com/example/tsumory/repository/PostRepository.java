package com.example.tsumory.repository;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 投稿 ID とユーザー ID の両方を条件に取得する。他人のつぶやきなら空。 */
    Optional<Post> findByIdAndUserId(Long id, Long userId);

    /** 投稿時刻が {@code from} 以上 {@code to} 未満のつぶやきを、投稿時刻の古い順に返す。 */
    @Query("""
            SELECT p FROM Post p
            WHERE p.user.id = :userId AND p.postedAt >= :from AND p.postedAt < :to
            ORDER BY p.postedAt, p.id
            """)
    List<Post> findInRange(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    /** 投稿時刻が {@code from} 以上 {@code to} 未満のつぶやきの件数を返す。 */
    @Query("""
            SELECT COUNT(p) FROM Post p
            WHERE p.user.id = :userId AND p.postedAt >= :from AND p.postedAt < :to
            """)
    long countInRange(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * 日記一覧：投稿時刻が {@code from} 以上 {@code to} 未満のつぶやきの、投稿時刻だけを返す。
     *
     * <p>日ごとの件数は、呼び出し側が日本時間の日付にして数える（SQL でタイムゾーンを変換して集計しない）。
     */
    @Query("""
            SELECT p.postedAt FROM Post p
            WHERE p.user.id = :userId AND p.postedAt >= :from AND p.postedAt < :to
            """)
    List<Instant> findPostedAtInRange(
            @Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    // 検索：本文に keyword を含むつぶやきを新しい順に limit 件まで。
    // keyword は呼び出し側で `!` `%` `_` をエスケープして渡す（日記の検索 DiaryRepository#searchByContent と同じ決まり）。
    // ESCAPE '!' は `!` を LIKE のエスケープ文字とする宣言。
    @Query("""
            SELECT p FROM Post p
            WHERE p.user.id = :userId
              AND LOWER(p.content) LIKE CONCAT('%', LOWER(:keyword), '%') ESCAPE '!'
            ORDER BY p.postedAt DESC, p.id DESC
            """)
    List<Post> searchByKeyword(@Param("userId") Long userId, @Param("keyword") String keyword, Limit limit);

    // 検索：本文に keyword を含み、カテゴリが category のつぶやきを新しい順に limit 件まで。keyword のエスケープは上と同じ。
    @Query("""
            SELECT p FROM Post p
            WHERE p.user.id = :userId AND p.category = :category
              AND LOWER(p.content) LIKE CONCAT('%', LOWER(:keyword), '%') ESCAPE '!'
            ORDER BY p.postedAt DESC, p.id DESC
            """)
    List<Post> searchByKeywordAndCategory(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("category") PostCategory category,
            Limit limit);

    /** 検索（キーワードなし）：カテゴリが {@code category} のつぶやきを、新しい順に {@code limit} 件まで返す。 */
    @Query("""
            SELECT p FROM Post p
            WHERE p.user.id = :userId AND p.category = :category
            ORDER BY p.postedAt DESC, p.id DESC
            """)
    List<Post> searchByCategory(@Param("userId") Long userId, @Param("category") PostCategory category, Limit limit);
}
