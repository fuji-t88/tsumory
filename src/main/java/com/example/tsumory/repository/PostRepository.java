package com.example.tsumory.repository;

import com.example.tsumory.domain.Post;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
}
