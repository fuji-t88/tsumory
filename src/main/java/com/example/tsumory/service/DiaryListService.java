package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.repository.DiaryRepository;
import com.example.tsumory.repository.PostRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 日記一覧（月ごとのリストとカレンダー）を組み立てる。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryListService {

    /** 日記の書き出しの長さ（文字数）。 */
    static final int EXCERPT_LENGTH = 60;

    private static final int DAYS_PER_WEEK = 7;

    private final DiaryRepository diaryRepository;
    private final PostRepository postRepository;
    private final DiaryService diaryService;
    private final Clock clock;

    /** 今月。日記一覧で月の指定がないときに使う。 */
    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    /** その月の日記一覧を返す。登録月より前・今月より後なら {@link DiaryMonthNotFoundException}。 */
    public DiaryMonthSummary summarize(Long userId, YearMonth month) {
        // 開ける日付の範囲（業務ルール）は DiaryService が決める。「今日」は範囲を作るときに 1 回だけ求められる
        var range = diaryService.availableRange(userId);
        if (!range.containsMonth(month)) {
            throw new DiaryMonthNotFoundException(month);
        }

        var first = month.atDay(1);
        var last = month.atEndOfMonth();
        var diaries = diaryRepository.findByUserIdAndDiaryDateBetweenOrderByDiaryDateDesc(userId, first, last).stream()
                .collect(Collectors.toMap(Diary::getDiaryDate, Function.identity()));
        // SQL でタイムゾーンを変換せず、投稿時刻を日本時間の日付にしてから数える
        var zone = clock.getZone();
        var postCounts = postRepository.findPostedAtByUserIdAndMonth(userId, month, zone).stream()
                .collect(Collectors.groupingBy(postedAt -> LocalDate.ofInstant(postedAt, zone), Collectors.counting()));

        return new DiaryMonthSummary(
                month,
                daySummaries(diaries, postCounts),
                calendarWeeks(month, diaries, postCounts, range),
                diaries.size(),
                month.equals(range.firstMonth()) ? null : month.minusMonths(1),
                month.equals(range.lastMonth()) ? null : month.plusMonths(1));
    }

    private static List<DiaryDaySummary> daySummaries(Map<LocalDate, Diary> diaries, Map<LocalDate, Long> postCounts) {
        var dates = new TreeSet<LocalDate>(Comparator.reverseOrder());
        dates.addAll(diaries.keySet());
        dates.addAll(postCounts.keySet());
        return dates.stream()
                .map(date -> {
                    var diary = diaries.get(date);
                    return new DiaryDaySummary(
                            date,
                            diary != null,
                            diary == null ? null : excerpt(diary.getContent()),
                            postCounts.getOrDefault(date, 0L));
                })
                .toList();
    }

    /** 日曜始まりの週に分ける。月の 1 日より前と末日より後は空きマスで埋め、どの週も 7 マスにする。 */
    private static List<DiaryCalendarWeek> calendarWeeks(
            YearMonth month, Map<LocalDate, Diary> diaries, Map<LocalDate, Long> postCounts, DiaryDateRange range) {
        var cells = new ArrayList<DiaryCalendarDay>();
        var leadingBlanks = month.atDay(1).getDayOfWeek().getValue() % DAYS_PER_WEEK; // 日曜は 0
        for (var i = 0; i < leadingBlanks; i++) {
            cells.add(DiaryCalendarDay.blank());
        }
        for (var day = 1; day <= month.lengthOfMonth(); day++) {
            var date = month.atDay(day);
            cells.add(new DiaryCalendarDay(
                    date,
                    diaries.containsKey(date),
                    postCounts.containsKey(date),
                    range.contains(date),
                    // 開ける最後の日が今日
                    date.equals(range.last())));
        }
        while (cells.size() % DAYS_PER_WEEK != 0) {
            cells.add(DiaryCalendarDay.blank());
        }
        var weeks = new ArrayList<DiaryCalendarWeek>();
        for (var i = 0; i < cells.size(); i += DAYS_PER_WEEK) {
            weeks.add(new DiaryCalendarWeek(cells.subList(i, i + DAYS_PER_WEEK)));
        }
        return weeks;
    }

    /** 日記の書き出し。改行は空白に置き換え、{@link #EXCERPT_LENGTH} 文字を超えたら切って末尾に「…」を付ける。 */
    static String excerpt(String content) {
        var oneLine = content.replaceAll("\\R", " ");
        if (oneLine.codePointCount(0, oneLine.length()) <= EXCERPT_LENGTH) {
            return oneLine;
        }
        // 絵文字などのサロゲートペアを途中で切らないよう、文字（コードポイント）単位で数える
        return oneLine.substring(0, oneLine.offsetByCodePoints(0, EXCERPT_LENGTH)) + "…";
    }
}
