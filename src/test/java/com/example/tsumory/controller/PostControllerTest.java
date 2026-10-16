package com.example.tsumory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.tsumory.config.CustomUserDetails;
import com.example.tsumory.config.CustomUserDetailsService;
import com.example.tsumory.config.SecurityConfig;
import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.repository.UserRepository;
import com.example.tsumory.service.DailyPostLimitExceededException;
import com.example.tsumory.service.DiaryItem;
import com.example.tsumory.service.DiaryService;
import com.example.tsumory.service.PostItem;
import com.example.tsumory.service.PostService;
import com.example.tsumory.service.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, CustomUserDetailsService.class})
class PostControllerTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final CustomUserDetails PRINCIPAL = new CustomUserDetails(USER_ID, "demo", "$2a$10$hash");

    private static final PostItem TODAY_POST =
            new PostItem(10L, "昼のカレーが辛すぎた", LocalTime.of(12, 40), false, PostCategory.PRIVATE);
    private static final PostItem EDITED_POST =
            new PostItem(11L, "寝坊した", LocalTime.of(8, 12), true, PostCategory.OTHER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private DiaryService diaryService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        when(postService.today()).thenReturn(TODAY);
        when(postService.findPosts(USER_ID, TODAY)).thenReturn(List.of(EDITED_POST, TODAY_POST));
        when(diaryService.findDiaryForSummary(USER_ID, TODAY)).thenReturn(Optional.empty());
    }

    // --- ホーム画面 ---

    @Test
    void 未ログインならログイン画面へリダイレクト() throws Exception {
        mockMvc.perform(get("/")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 今日のつぶやきを件数と時刻と編集削除の操作付きで表示する() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("2026年10月7日（水）")))
                .andExpect(content().string(containsString("2 件")))
                .andExpect(content().string(containsString("12:40")))
                .andExpect(content().string(containsString("昼のカレーが辛すぎた")))
                .andExpect(content().string(containsString("/posts/10/edit")))
                .andExpect(content().string(containsString("action=\"/posts/10/delete\"")));
    }

    @Test
    void 各つぶやきにカテゴリのバッジを出す() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                // th:switch で、カテゴリに合う色のバッジだけを出す
                .andExpect(content()
                        .string(matchesPattern("(?s).*<span class=\"badge bg-success\">\\s*<span>プライベート</span>.*")))
                .andExpect(content()
                        .string(matchesPattern("(?s).*<span class=\"badge bg-secondary\">\\s*<span>その他</span>.*")))
                .andExpect(content().string(not(containsString("badge bg-primary"))))
                .andExpect(content().string(not(containsString("badge bg-info"))));
    }

    @Test
    void 各つぶやきに今のカテゴリを選んだカテゴリ変更フォームを出す() throws Exception {
        var html = mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("action=\"/posts/10/category\"")
                .contains("action=\"/posts/11/category\"")
                .contains("<label for=\"category-10\" class=\"visually-hidden\">カテゴリ</label>")
                // 選んだ瞬間に送信するので、送信ボタンは置かない
                .contains("id=\"category-10\" name=\"category\" onchange=\"this.form.submit()\"")
                .doesNotContain(">変更</button>")
                // 10 は「プライベート」、11 は「その他」を選んだ状態
                .containsPattern("id=\"category-10\"[^<]*>\\s*(<option[^>]*>[^<]*</option>\\s*)*"
                        + "<option value=\"PRIVATE\"\\s+selected=\"selected\">")
                .containsPattern("id=\"category-11\"[^<]*>\\s*(<option[^>]*>[^<]*</option>\\s*)*"
                        + "<option value=\"OTHER\"\\s+selected=\"selected\">")
                // th:action なので CSRF トークンが入る
                .containsPattern("action=\"/posts/10/category\"[\\s\\S]*?name=\"_csrf\"");
    }

    @Test
    void ナビゲーションに日記一覧とカレンダーへのリンクを出し今日を選択中にする() throws Exception {
        var html = mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .containsPattern("href=\"/\" class=\"nav-link active\"\\s+aria-current=\"page\">今日</a>")
                .contains("href=\"/diary\" class=\"nav-link\">日記一覧</a>")
                .contains("href=\"/diaries\" class=\"nav-link\">カレンダー</a>")
                .contains("href=\"/search\" class=\"nav-link\">検索</a>");
    }

    @Test
    void ナビバーの履歴ボタンと今日のつぶやきの見出しから過去の日記へ移れる() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                // グローバル導線（ナビバー）
                .andExpect(content()
                        .string(containsString("<a href=\"/diary\" class=\"btn btn-outline-secondary btn-sm\">履歴</a>")))
                // コンテキスト導線（「今日のつぶやき」の見出し）
                .andExpect(content().string(containsString("<a href=\"/diary\" class=\"small\">過去の日記を見る →</a>")));
    }

    @Test
    void 本文の入力欄の上限はフォームの検証と同じ文字数にする() throws Exception {
        // 画面の maxlength も Post.CONTENT_MAX_LENGTH から出す（InputLimitsAdvice）
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("maxlength=\"" + Post.CONTENT_MAX_LENGTH + "\"")));
    }

    @Test
    void 開発用のコメントを画面のHTMLに出さない() throws Exception {
        // テンプレートのコメントは Thymeleaf のコメント（<!--/* */-->）で書き、ブラウザに送らない
        mockMvc.perform(get("/").with(user(PRINCIPAL))).andExpect(content().string(not(containsString("<!--"))));
    }

    @Test
    void ナビゲーションにログアウトボタンを表示する() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("ログアウト")));
    }

    @Test
    void 今日のつぶやきが0件ならその旨を表示し日記を生成ボタンを無効にする() throws Exception {
        when(postService.findPosts(USER_ID, TODAY)).thenReturn(List.of());

        var html = mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("まだつぶやきはありません。")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).containsPattern("id=\"genBtn\"[^>]*disabled");
    }

    @Test
    void つぶやきがあり日記がなければ未生成と日記を生成ボタンを出す() throws Exception {
        var html = mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("未生成")))
                .andExpect(content().string(containsString("action=\"/diary/2026-10-07/generate\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).doesNotContainPattern("id=\"genBtn\"[^>]*disabled");
        assertThat(html).contains("日記を作成中です");
    }

    @Test
    void 日記があれば生成済と日記を見るリンクに切り替える() throws Exception {
        when(diaryService.findDiaryForSummary(USER_ID, TODAY))
                .thenReturn(Optional.of(new DiaryItem(TODAY, "日記の本文", LocalTime.of(21, 30))));

        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("生成済")))
                .andExpect(content().string(containsString("href=\"/diary/2026-10-07\"")))
                .andExpect(content().string(containsString("今日の日記を見る")))
                .andExpect(content().string(not(containsString("action=\"/diary/2026-10-07/generate\""))));
    }

    @Test
    void フラッシュメッセージがあれば表示する() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)).flashAttr("message", PostController.POST_NOT_FOUND_MESSAGE))
                .andExpect(content().string(containsString("alert-warning")))
                .andExpect(content().string(containsString("つぶやきが見つかりませんでした")));
    }

    @Test
    void フラッシュメッセージがなければ出さない() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(not(containsString("alert-warning"))));
    }

    @Test
    void 今日は1回だけ求め日付を確かめ直す取得は使わない() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL))).andExpect(status().isOk());

        verify(postService, times(1)).today();
        verify(diaryService, never()).findByDate(any(), any());
    }

    // --- URL の値が不正 ---

    @Test
    void IDが数字でないURLは404() throws Exception {
        mockMvc.perform(get("/posts/abc/edit").with(user(PRINCIPAL))).andExpect(status().isNotFound());
        mockMvc.perform(post("/posts/abc/edit")
                        .param("content", "書き換え")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/posts/abc/delete").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void IDがLongに収まらないURLは404() throws Exception {
        mockMvc.perform(get("/posts/99999999999999999999/edit").with(user(PRINCIPAL)))
                .andExpect(status().isNotFound());
    }

    // --- 投稿 ---

    @Test
    void 投稿するとトップへリダイレクト() throws Exception {
        mockMvc.perform(post("/posts")
                        .param("content", "  寝坊した  ")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));

        // 前後の空白は PostService が保存の前に取り除くので、controller は入力値をそのまま渡す
        verify(postService).create(USER_ID, "  寝坊した  ");
    }

    @Test
    void 改行を含む本文はエラーを表示して投稿しない() throws Exception {
        // 日記のプロンプトに偽の行を紛れ込ませられないよう、CRLF・LF・CR のどれも受け付けない
        for (var content : List.of("朝\r\n- [仕事] 嘘の予定", "朝\n昼", "朝\r昼")) {
            mockMvc.perform(post("/posts")
                            .param("content", content)
                            .with(user(PRINCIPAL))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("home"))
                    .andExpect(content().string(containsString("改行は入れられません")));
        }

        verify(postService, never()).create(anyLong(), anyString());
    }

    @Test
    void 改行を含む本文には編集でも直せない() throws Exception {
        when(postService.getEditablePost(USER_ID, 10L)).thenReturn(TODAY_POST);

        mockMvc.perform(post("/posts/10/edit")
                        .param("content", "朝\r\n昼")
                        .param("category", "PRIVATE")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/posts/10/edit\"")))
                .andExpect(content().string(containsString("改行は入れられません")));

        verify(postService, never()).edit(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void 空白と改行だけならエラーを表示して投稿しない() throws Exception {
        mockMvc.perform(post("/posts")
                        .param("content", " 　\r\n ")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("内容を入力してください")));

        verify(postService, never()).create(anyLong(), anyString());
    }

    @Test
    void 全角スペースだけならエラーを表示して投稿しない() throws Exception {
        // service は strip() で全角スペースも取り除くので、フォームで止めないと空の本文で Post を作ろうとして 500 になる
        mockMvc.perform(post("/posts")
                        .param("content", "　　")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("内容を入力してください")));

        verify(postService, never()).create(anyLong(), anyString());
    }

    @Test
    void 文字数を超えたらエラーを表示し入力値を残す() throws Exception {
        var content = "あ".repeat(101);

        mockMvc.perform(post("/posts")
                        .param("content", content)
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("100 文字以内で入力してください")))
                .andExpect(content().string(containsString(content)));

        verify(postService, never()).create(anyLong(), anyString());
    }

    @Test
    void 上限に達していなければ投稿フォームを使える() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(not(containsString("disabled=\"disabled\">つぶやく"))))
                .andExpect(content().string(not(containsString("件に達しました"))));
    }

    @Test
    void 今日のつぶやきが上限に達したら投稿フォームを無効にしてその旨を出す() throws Exception {
        // 上限に達したかの判定は PostService が持つ（投稿を断る判定と同じもの）
        when(postService.isPostLimitReached(USER_ID, TODAY)).thenReturn(true);

        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("disabled=\"disabled\">つぶやく")))
                .andExpect(content().string(containsString("今日のつぶやきは 16 件に達しました。明日またつぶやけます。")));
    }

    @Test
    void 上限に達していたらメッセージを付けてトップへリダイレクト() throws Exception {
        doThrow(new DailyPostLimitExceededException(USER_ID, PostService.DAILY_POST_LIMIT))
                .when(postService)
                .create(USER_ID, "17 件目");

        mockMvc.perform(post("/posts")
                        .param("content", "17 件目")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", "今日の投稿は 16 件に達しました"));
    }

    @Test
    void CSRFトークンなしの投稿は403() throws Exception {
        mockMvc.perform(post("/posts").param("content", "寝坊した").with(user(PRINCIPAL)))
                .andExpect(status().isForbidden());

        verify(postService, never()).create(anyLong(), anyString());
    }

    // --- 編集モード ---

    @Test
    void 編集モードではその行だけ現在の本文が入った編集フォームにする() throws Exception {
        when(postService.getEditablePost(USER_ID, 10L)).thenReturn(TODAY_POST);

        var html = mockMvc.perform(get("/posts/10/edit").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("action=\"/posts/10/edit\"")
                .contains(">昼のカレーが辛すぎた</textarea>")
                .contains("キャンセル")
                .contains("保存")
                // 編集中の行は通常表示（編集・削除の操作）を出さない
                .doesNotContain("action=\"/posts/10/delete\"")
                // ほかの行は通常表示のまま
                .contains("/posts/11/edit")
                .contains("action=\"/posts/11/delete\"");
    }

    @Test
    void 編集モードでは今のカテゴリを選んだセレクトボックスを出す() throws Exception {
        when(postService.getEditablePost(USER_ID, 10L)).thenReturn(TODAY_POST);

        var html = mockMvc.perform(get("/posts/10/edit").with(user(PRINCIPAL)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("<label for=\"category\" class=\"visually-hidden\">カテゴリ</label>")
                .contains("name=\"category\"")
                .contains("<option value=\"WORK\">仕事</option>")
                .contains("<option value=\"PRIVATE\" selected=\"selected\">プライベート</option>")
                .contains("<option value=\"LEARNING\">学習</option>")
                .contains("<option value=\"OTHER\">その他</option>");
    }

    @Test
    void 他人や一昨日のつぶやきの編集モードはメッセージを付けてトップへリダイレクト() throws Exception {
        when(postService.getEditablePost(USER_ID, 99L)).thenThrow(new ResourceNotFoundException("Post", 99L));

        mockMvc.perform(get("/posts/99/edit").with(user(PRINCIPAL)))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", PostController.POST_NOT_FOUND_MESSAGE));
    }

    // --- 編集 ---

    @Test
    void 本文とカテゴリを一緒に保存してトップへリダイレクト() throws Exception {
        mockMvc.perform(post("/posts/10/edit")
                        .param("content", " 昼のカレーがおいしかった ")
                        .param("category", "WORK")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));

        verify(postService).edit(USER_ID, 10L, " 昼のカレーがおいしかった ", PostCategory.WORK);
    }

    @Test
    void 候補にないカテゴリはエラーを表示して保存しない() throws Exception {
        when(postService.getEditablePost(USER_ID, 10L)).thenReturn(TODAY_POST);

        mockMvc.perform(post("/posts/10/edit")
                        .param("content", "昼のカレーがおいしかった")
                        .param("category", "HOBBY")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/posts/10/edit\"")))
                .andExpect(content().string(containsString("カテゴリを選んでください")))
                // 型変換の内部的なメッセージは出さない
                .andExpect(content().string(not(containsString("Failed to convert"))));

        verify(postService, never()).edit(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void カテゴリがなければエラーを表示して保存しない() throws Exception {
        when(postService.getEditablePost(USER_ID, 10L)).thenReturn(TODAY_POST);

        mockMvc.perform(post("/posts/10/edit")
                        .param("content", "昼のカレーがおいしかった")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("カテゴリを選んでください")));

        verify(postService, never()).edit(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void 編集の入力エラーは編集モードのまま入力値を残して再表示する() throws Exception {
        when(postService.getEditablePost(USER_ID, 10L)).thenReturn(TODAY_POST);
        var content = "い".repeat(101);

        mockMvc.perform(post("/posts/10/edit")
                        .param("content", content)
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/posts/10/edit\"")))
                .andExpect(content().string(containsString("100 文字以内で入力してください")))
                .andExpect(content().string(containsString(content)));

        verify(postService, never()).edit(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void 他人のつぶやきの編集はメッセージを付けてトップへリダイレクト() throws Exception {
        doThrow(new ResourceNotFoundException("Post", 99L))
                .when(postService)
                .edit(USER_ID, 99L, "書き換え", PostCategory.OTHER);

        mockMvc.perform(post("/posts/99/edit")
                        .param("content", "書き換え")
                        .param("category", "OTHER")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", PostController.POST_NOT_FOUND_MESSAGE));
    }

    @Test
    void 他人のつぶやきは入力エラーでも再表示せずにトップへリダイレクト() throws Exception {
        when(postService.getEditablePost(USER_ID, 99L)).thenThrow(new ResourceNotFoundException("Post", 99L));

        mockMvc.perform(post("/posts/99/edit")
                        .param("content", "")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", PostController.POST_NOT_FOUND_MESSAGE));
    }

    @Test
    void CSRFトークンなしの編集は403() throws Exception {
        mockMvc.perform(post("/posts/10/edit").param("content", "書き換え").with(user(PRINCIPAL)))
                .andExpect(status().isForbidden());

        verify(postService, never()).edit(anyLong(), anyLong(), any(), any());
    }

    // --- カテゴリの修正 ---

    @Test
    void 日記表示から直したらその日の日記表示に戻る() throws Exception {
        mockMvc.perform(post("/posts/10/category")
                        .param("category", "WORK")
                        .param("date", "2026-10-03")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/diary/2026-10-03"));

        verify(postService).updateCategory(USER_ID, 10L, PostCategory.WORK);
    }

    @Test
    void 戻り先の日付が日付の形式でなければ404で何も変えない() throws Exception {
        // 任意の URL へ飛ばさない（オープンリダイレクトの対策）
        mockMvc.perform(post("/posts/10/category")
                        .param("category", "WORK")
                        .param("date", "//evil.example.com")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(postService, never()).updateCategory(anyLong(), anyLong(), any());
    }

    @Test
    void 他人のつぶやきなら戻り先の日付があってもメッセージを付けてトップへ() throws Exception {
        doThrow(new ResourceNotFoundException("Post", 99L))
                .when(postService)
                .updateCategory(USER_ID, 99L, PostCategory.WORK);

        mockMvc.perform(post("/posts/99/category")
                        .param("category", "WORK")
                        .param("date", "2026-10-03")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", PostController.POST_NOT_FOUND_MESSAGE));
    }

    @Test
    void カテゴリを直すとトップへリダイレクト() throws Exception {
        mockMvc.perform(post("/posts/10/category")
                        .param("category", "WORK")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeCount(0));

        // 送られた文字列は PostCategory に変換されて渡る
        verify(postService).updateCategory(USER_ID, 10L, PostCategory.WORK);
    }

    @Test
    void 他人のつぶやきのカテゴリはメッセージを付けてトップへリダイレクト() throws Exception {
        doThrow(new ResourceNotFoundException("Post", 99L))
                .when(postService)
                .updateCategory(USER_ID, 99L, PostCategory.WORK);

        mockMvc.perform(post("/posts/99/category")
                        .param("category", "WORK")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", PostController.POST_NOT_FOUND_MESSAGE));
    }

    @Test
    void 候補にないカテゴリは404で何も変えない() throws Exception {
        mockMvc.perform(post("/posts/10/category")
                        .param("category", "HOBBY")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(postService, never()).updateCategory(anyLong(), anyLong(), any());
    }

    @Test
    void カテゴリがなければ400で何も変えない() throws Exception {
        mockMvc.perform(post("/posts/10/category").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isBadRequest());

        verify(postService, never()).updateCategory(anyLong(), anyLong(), any());
    }

    @Test
    void CSRFトークンなしのカテゴリの修正は403() throws Exception {
        mockMvc.perform(post("/posts/10/category").param("category", "WORK").with(user(PRINCIPAL)))
                .andExpect(status().isForbidden());

        verify(postService, never()).updateCategory(anyLong(), anyLong(), any());
    }

    // --- 削除 ---

    @Test
    void 削除するとトップへリダイレクト() throws Exception {
        mockMvc.perform(post("/posts/10/delete").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/"));

        verify(postService).delete(USER_ID, 10L);
    }

    @Test
    void 他人のつぶやきの削除はメッセージを付けてトップへリダイレクト() throws Exception {
        doThrow(new ResourceNotFoundException("Post", 99L)).when(postService).delete(USER_ID, 99L);

        mockMvc.perform(post("/posts/99/delete").with(user(PRINCIPAL)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", PostController.POST_NOT_FOUND_MESSAGE));
    }

    @Test
    void CSRFトークンなしの削除は403() throws Exception {
        mockMvc.perform(post("/posts/10/delete").with(user(PRINCIPAL))).andExpect(status().isForbidden());

        verify(postService, never()).delete(anyLong(), anyLong());
    }
}
