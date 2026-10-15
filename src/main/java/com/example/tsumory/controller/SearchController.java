package com.example.tsumory.controller;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.form.SearchForm;
import com.example.tsumory.service.SearchService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final Clock clock;

    /**
     * キーワードとカテゴリで、つぶやきと日記を検索する。入力エラーのときは、検索せずにフォームとメッセージだけを出す。
     *
     * <p>キーワードもカテゴリも指定がないとき（初めて開いたときなど）は、検索の代わりに日記を新しい順にページングして出す。ページは
     * {@code ?page=} の番号だけを受け取り、件数（{@link SearchService#RECENT_PAGE_SIZE}）と並び順は固定する（{@code size}・{@code sort} を
     * 受け付けない）。負の番号は 0 として扱う。
     */
    @GetMapping("/search")
    public String search(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute("searchForm") SearchForm searchForm,
            BindingResult binding,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model) {
        var userId = principal.userId();
        var keyword = searchForm.q() == null ? "" : searchForm.q().strip();
        var category = toCategory(searchForm.category());
        model.addAttribute("categories", PostCategory.values());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("results", null);
        if (binding.hasErrors()) {
            return "search";
        }
        if (keyword.isEmpty() && category == null) {
            var pageable = PageRequest.of(Math.max(0, page), SearchService.RECENT_PAGE_SIZE);
            model.addAttribute("recentDiaries", searchService.recentDiaries(userId, pageable));
            return "search";
        }
        var result = searchService.search(userId, keyword, category);
        model.addAttribute("results", result.hits());
        model.addAttribute("postsTruncated", result.postsTruncated());
        model.addAttribute("diariesTruncated", result.diariesTruncated());
        return "search";
    }

    /** カテゴリの名前を {@link PostCategory} にする。候補にない値や空は「すべて」（{@code null}）として扱う。 */
    private static PostCategory toCategory(String name) {
        return Arrays.stream(PostCategory.values())
                .filter(category -> category.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
