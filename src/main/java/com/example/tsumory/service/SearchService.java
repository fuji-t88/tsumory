package com.example.tsumory.service;

import com.example.tsumory.domain.Diary;
import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.repository.PostRepository;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * つぶやきと日記をキーワードとカテゴリで検索する。ログイン中のユーザーのものだけを対象にする。
 *
 * <p>検索キーワードとカテゴリはログに出さない（つぶやきや日記の内容を推測できるため。CLAUDE.md の禁止事項「ログ」）。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    /** 種類ごとに返す件数の上限。 */
    static final int MAX_RESULTS = 50;

    /** 日記の本文から切り出す、キーワードの前と後の文字数。 */
    static final int EXCERPT_BEFORE = 30;

    static final int EXCERPT_AFTER = 60;

    /** キーワードなしで開いたときに、1 ページに出す日記の件数。 */
    static final int RECENT_PAGE_SIZE = 20;

    private final PostRepository postRepository;
    private final DiaryService diaryService;

    /**
     * キーワードとカテゴリで検索する。どちらも指定がないときは呼ばない（controller は代わりに {@link #recentDiaries} を出す）。
     *
     * @param keyword 前後の空白を取り除いたキーワード。なければ空文字
     * @param category 絞り込むカテゴリ。「すべて」なら {@code null}。カテゴリを選んでいるときは、日記を検索しない（日記にはカテゴリがないため）
     */
    public SearchResult search(Long userId, String keyword, PostCategory category) {
        var escaped = keyword.isEmpty() ? null : escapeLike(keyword);

        // つぶやきは、上限を超えたかを知るため 1 件多く取得する
        var limit = Limit.of(MAX_RESULTS + 1);
        List<Post> posts;
        if (escaped == null) {
            posts = postRepository.searchByCategory(userId, category, limit);
        } else if (category == null) {
            posts = postRepository.searchByKeyword(userId, escaped, limit);
        } else {
            posts = postRepository.searchByKeywordAndCategory(userId, escaped, category, limit);
        }
        // 日記は 1 ページ目（上限件数）だけを取り、次のページがあれば上限を超えたとみなす。
        // キーワードのエスケープは DiaryService#searchDiaries が行うので、エスケープ前のキーワードを渡す
        var diaries = escaped != null && category == null
                ? diaryService.searchDiaries(userId, keyword, PageRequest.of(0, MAX_RESULTS))
                : Page.<Diary>empty();

        var hits = Stream.concat(
                        posts.stream().limit(MAX_RESULTS).map(post -> toHit(post, keyword)),
                        diaries.stream().map(diary -> toHit(diary, keyword)))
                .sorted(Comparator.comparing(SearchHit::sortKey).reversed())
                .toList();
        return new SearchResult(hits, posts.size() > MAX_RESULTS, diaries.hasNext());
    }

    /**
     * キーワードなしで開いたときの一覧：日記を新しい順に、{@link #RECENT_PAGE_SIZE} 件ずつ返す。ページ番号だけを使い、件数と並び順は固定する
     * （リクエストの {@code size}・{@code sort} を受け付けない）。
     */
    public Page<SearchHit> recentDiaries(Long userId, Pageable pageable) {
        // キーワードなしなので、DiaryService#searchDiaries は絞り込みのない一覧を返す
        return diaryService
                .searchDiaries(userId, null, PageRequest.of(pageable.getPageNumber(), RECENT_PAGE_SIZE))
                .map(diary -> toHit(diary, ""));
    }

    /**
     * つぶやきの検索用に、{@code LIKE} に渡すキーワードの {@code !}・{@code %}・{@code _} の前に {@code !} を付ける（クエリの
     * {@code ESCAPE '!'} に対応）。日記の検索は {@code DiaryService#searchDiaries} の中で同じ決まりでエスケープする。{@code !} 自身を先に
     * 置き換えないと、付けた {@code !} まで二重にエスケープされる。
     */
    static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static SearchHit toHit(Post post, String keyword) {
        var postedAt = post.getPostedAt().atZone(PostService.ZONE);
        return new SearchHit.PostHit(
                postedAt.toLocalDate(),
                LocalTime.of(postedAt.getHour(), postedAt.getMinute()),
                HighlightedText.of(post.getContent(), keyword),
                post.getCategory(),
                post.getPostedAt());
    }

    private static SearchHit toHit(Diary diary, String keyword) {
        return new SearchHit.DiaryHit(
                diary.getDiaryDate(), HighlightedText.of(excerpt(diary.getContent(), keyword), keyword));
    }

    /**
     * 日記の本文から、最初に一致した位置の前 {@link #EXCERPT_BEFORE} 文字・後ろ {@link #EXCERPT_AFTER} 文字を切り出す。途中で切ったところには「…」を付ける。
     * 改行は空白に置き換える（結果のカードを 1 段落で見せるため）。
     */
    static String excerpt(String content, String keyword) {
        var oneLine = content.replaceAll("\\R", " ");
        var index = HighlightedText.indexOfIgnoreCase(oneLine, keyword);
        if (index < 0) {
            // DB の lower() と Java の比べ方がずれて見つからなかったときは、先頭から切り出す
            index = 0;
        }
        var start = Math.max(0, index - EXCERPT_BEFORE);
        var end = Math.min(oneLine.length(), index + keyword.length() + EXCERPT_AFTER);
        // サロゲートペアを途中で切らない
        if (start > 0 && Character.isLowSurrogate(oneLine.charAt(start))) {
            start--;
        }
        if (end < oneLine.length() && Character.isLowSurrogate(oneLine.charAt(end))) {
            end++;
        }
        return (start > 0 ? "…" : "") + oneLine.substring(start, end) + (end < oneLine.length() ? "…" : "");
    }
}
