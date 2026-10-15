package com.example.tsumory.repository;

import com.example.tsumory.domain.Diary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByUserIdAndDiaryDate(Long userId, LocalDate diaryDate);

    // 日記一覧（月ごと）用：日付が from 以上 to 以下の日記を新しい順に。
    // ユーザー入力を含まないので、派生クエリで足りる。
    List<Diary> findByUserIdAndDiaryDateBetweenOrderByDiaryDateDesc(Long userId, LocalDate from, LocalDate to);

    // 一覧表示用：ユーザーの日記を新しい順に（ページング）。
    // ユーザー入力を含まないのでエスケープ不要＝派生クエリで足りる。
    Page<Diary> findByUserIdOrderByDiaryDateDesc(Long userId, Pageable pageable);

    // keyword は呼び出し側で `!` `%` `_` をエスケープして渡す。
    // ESCAPE '!' は `!` を LIKE のエスケープ文字とする宣言。
    @Query("""
            SELECT d FROM Diary d
            WHERE d.user.id = :userId
              AND LOWER(d.content) LIKE CONCAT('%', LOWER(:keyword), '%') ESCAPE '!'
            ORDER BY d.diaryDate DESC
            """)
    Page<Diary> searchByContent(@Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);
}
