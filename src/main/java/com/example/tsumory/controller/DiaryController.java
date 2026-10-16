package com.example.tsumory.controller;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.form.DiaryListForm;
import com.example.tsumory.service.DiaryGenerationException;
import com.example.tsumory.service.DiaryGenerationService;
import com.example.tsumory.service.DiaryItem;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.InvalidDiaryDateException;
import com.example.tsumory.service.NoPostsException;
import com.example.tsumory.service.PostService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/diary")
@RequiredArgsConstructor
public class DiaryController {

    static final String NO_POSTS_MESSAGE = "つぶやきがないため日記を生成できません";
    static final String FUTURE_DATE_MESSAGE = "未来の日付の日記は表示できません";

    /** 日記の一覧（{@code GET /diary}）で 1 ページに出す件数。 */
    static final int DIARY_LIST_PAGE_SIZE = 10;

    static final String GENERATION_FAILED_MESSAGE = "日記の生成に失敗しました。時間をおいて再度お試しください";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final DiaryService diaryService;
    private final DiaryGenerationService diaryGenerationService;
    private final PostService postService;
    private final Clock clock;

    /**
     * 日記の一覧（{@code GET /diary}）。キーワードがあれば本文で絞り込み、新しい順に {@link #DIARY_LIST_PAGE_SIZE} 件ずつ出す。
     *
     * <p>一覧と検索の振り分けとキーワードのエスケープは {@link DiaryService#searchDiaries} が行う。テンプレートにはエンティティではなく
     * 表示用の {@link DiaryItem} を渡す。
     */
    @GetMapping
    public String diaryList(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute("diaryListForm") DiaryListForm form,
            BindingResult binding,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model) {
        var userId = principal.userId();
        var pageable = PageRequest.of(Math.max(0, page), DIARY_LIST_PAGE_SIZE);
        model.addAttribute("keyword", form.keyword());
        if (binding.hasErrors()) {
            // キーワードが長すぎるときは、検索せずにエラーを出す（入力欄の下に出す）
            model.addAttribute("diaries", Page.<DiaryItem>empty(pageable));
            return "diary-list";
        }
        var diaries = diaryService
                .searchDiaries(userId, form.keyword(), pageable)
                .map(diary -> DiaryItem.from(diary, clock.getZone()));
        model.addAttribute("diaries", diaries);
        return "diary-list";
    }

    @GetMapping("/{date}")
    public String view(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails principal,
            Model model) {
        var diary = diaryService.findByDate(principal.userId(), date);
        model.addAttribute("date", date);
        // 今日の画面かどうかで、ヘッダーの選択中のメニューを決める
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("diary", diary.orElse(null));
        // 作成時刻。日記の日付と作成日が同じなら「HH:mm」、違えば（後から遡って書いた日記）「M月d日 HH:mm」
        diary.ifPresent(d -> {
            var generatedAt = d.getGeneratedAt().atZone(clock.getZone());
            var format = generatedAt.toLocalDate().equals(date) ? TIME_FORMATTER : DATE_TIME_FORMATTER;
            model.addAttribute("generatedAtLabel", generatedAt.format(format));
        });
        // この日のつぶやき（投稿時刻の古い順）。日記表示では読むだけ
        model.addAttribute(
                "posts",
                postService.findPosts(principal.userId(), date).stream()
                        .map(PostView::from)
                        .toList());
        // 「つぶやきを直す」の導線は、つぶやきを直せる日付（今日と昨日）のときだけ出す
        model.addAttribute("postsEditable", postService.isEditableDate(date));
        model.addAttribute("categories", PostCategory.values());
        return "diary";
    }

    /**
     * その画面の日付の日記を書く・作り直す（遡り生成）。成功したら {@code /diary/{date}} に戻る。
     *
     * <ul>
     *   <li>未来の日付・つぶやき 0 件：メッセージを付けて {@code /}（ホーム）へ
     *   <li>AI の呼び出しの失敗：メッセージを付けて {@code /diary/{date}} へ（前の日記はそのまま残る）
     *   <li>登録日より前の日付は {@code DiaryNotFoundException}、形式が不正な日付は {@link NotFoundExceptionHandler} で 404
     * </ul>
     *
     * <p>日付は URL で受け取るので、生成中に 0 時をまたいでも、押した画面の日付の日記として保存される。
     */
    @PostMapping("/{date}/generate")
    public String generateDiaryForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails principal,
            RedirectAttributes flash) {
        var userId = principal.userId();
        try {
            diaryGenerationService.generateForDate(userId, date);
        } catch (InvalidDiaryDateException e) {
            flash.addFlashAttribute("message", FUTURE_DATE_MESSAGE);
            return "redirect:/";
        } catch (NoPostsException e) {
            flash.addFlashAttribute("message", NO_POSTS_MESSAGE);
            return "redirect:/";
        } catch (DiaryGenerationException e) {
            // 500 にせず、メッセージを付けてこの日の画面に戻す（phase2-spec.md の「6. 処理の詳細 > 日記生成・再生成」）
            flash.addFlashAttribute("message", GENERATION_FAILED_MESSAGE);
        }
        return "redirect:/diary/" + date;
    }
}
