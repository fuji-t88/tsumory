package com.example.tsumory.repository;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 投稿 ID とユーザー ID の両方を条件に取得する。他人のつぶやきなら空。 */
    Optional<Post> findByIdAndUserId(Long id, Long userId);

    // 日付（LocalDate）から投稿時刻（Instant）の範囲への変換は、以下の default メソッドにまとめる。
    // Service は日付とタイムゾーン（clock.getZone()）を渡すだけでよく、範囲の組み立てはこのリポジトリに閉じる。
    // 範囲は「その日 0:00 以上、翌日 0:00 未満」の半開区間で比べる。終わりを LocalTime.MAX（23:59:59.999999999）にして
    // BETWEEN で両端を含めると、DB（マイクロ秒まで）で丸められて翌日 0:00 ちょうどの投稿まで入ることがあるため。

    /** その日（{@code zone} での日付）のつぶやきを、投稿時刻の古い順に返す。 */
    default List<Post> findByUserIdAndDate(Long userId, LocalDate date, ZoneId zone) {
        return findInRange(userId, startOf(date, zone), startOf(date.plusDays(1), zone));
    }

    /** その日（{@code zone} での日付）のつぶやきの件数を返す。 */
    default long countByUserIdAndDate(Long userId, LocalDate date, ZoneId zone) {
        return countInRange(userId, startOf(date, zone), startOf(date.plusDays(1), zone));
    }

    /** 日記一覧：その月（{@code zone} での日付）のつぶやきの、投稿時刻だけを返す。 */
    default List<Instant> findPostedAtByUserIdAndMonth(Long userId, YearMonth month, ZoneId zone) {
        return findPostedAtInRange(
                userId,
                startOf(month.atDay(1), zone),
                startOf(month.plusMonths(1).atDay(1), zone));
    }

    private static Instant startOf(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** 投稿時刻が {@code from} 以上 {@code to} 未満のつぶやきを、投稿時刻の古い順に返す。日付で取るときは {@link #findByUserIdAndDate} を使う。 */
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
