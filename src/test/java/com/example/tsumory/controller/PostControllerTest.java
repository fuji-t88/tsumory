package com.example.tsumory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import com.example.tsumory.repository.UserRepository;
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
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final CustomUserDetails PRINCIPAL = new CustomUserDetails(USER_ID, "demo", "$2a$10$hash");

    private static final PostItem TODAY_POST = new PostItem(10L, "昼のカレーが辛すぎた", LocalTime.of(12, 40), false);
    private static final PostItem EDITED_POST = new PostItem(11L, "寝坊した", LocalTime.of(8, 12), true);
    private static final PostItem YESTERDAY_POST = new PostItem(20L, "夜ふかしした", LocalTime.of(23, 50), false);

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
        when(postService.findPosts(USER_ID, YESTERDAY)).thenReturn(List.of());
        when(diaryService.findDiaryForSummary(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(diaryService.findDiaryForSummary(USER_ID, YESTERDAY)).thenReturn(Optional.empty());
    }

    // --- 今日のつぶやき画面 ---

    @Test
    void 未ログインならログイン画面へリダイレクト() throws Exception {
        mockMvc.perform(get("/")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 今日の日付とつぶやきを表示する() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("2026年10月7日（水）")))
                .andExpect(content().string(containsString("12:40")))
                .andExpect(content().string(containsString("昼のカレーが辛すぎた")))
                .andExpect(content().string(containsString("/posts/10/edit")))
                .andExpect(content().string(containsString("まだ日記はありません")));
    }

    @Test
    void ヘッダーにユーザー名とログアウトボタンを表示する() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString(">demo</span>")))
                .andExpect(content().string(containsString("ログアウト")));
    }

    @Test
    void 編集したつぶやきには編集済みと添える() throws Exception {
        when(postService.findPosts(USER_ID, TODAY)).thenReturn(List.of(EDITED_POST));

        mockMvc.perform(get("/").with(user(PRINCIPAL))).andExpect(content().string(containsString("編集済み")));
    }

    @Test
    void 投稿しただけのつぶやきには編集済みと添えない() throws Exception {
        when(postService.findPosts(USER_ID, TODAY)).thenReturn(List.of(TODAY_POST));

        mockMvc.perform(get("/").with(user(PRINCIPAL))).andExpect(content().string(not(containsString("編集済み"))));
    }

    @Test
    void 今日のつぶやきが0件ならその旨を表示する() throws Exception {
        when(postService.findPosts(USER_ID, TODAY)).thenReturn(List.of());

        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("まだつぶやきがありません")))
                .andExpect(content().string(containsString("/diary/2026-10-07")));
    }

    @Test
    void 日記があれば作成時刻を表示する() throws Exception {
        when(diaryService.findDiaryForSummary(USER_ID, TODAY))
                .thenReturn(Optional.of(new DiaryItem(TODAY, "日記の本文", LocalTime.of(21, 30))));

        mockMvc.perform(get("/").with(user(PRINCIPAL))).andExpect(content().string(containsString("日記あり（21:30 に作成）")));
    }

    @Test
    void 昨日のつぶやきも日記もなければ昨日の区画を出さない() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(not(containsString("id=\"yesterday\""))));
    }

    @Test
    void 昨日のつぶやきがあれば昨日の区画に編集リンク付きで出す() throws Exception {
        when(postService.findPosts(USER_ID, YESTERDAY)).thenReturn(List.of(YESTERDAY_POST));

        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("id=\"yesterday\"")))
                .andExpect(content().string(containsString("昨日 2026年10月6日（火）")))
                .andExpect(content().string(containsString("夜ふかしした")))
                .andExpect(content().string(containsString("/posts/20/edit")))
                .andExpect(content().string(containsString("/diary/2026-10-06")));
    }

    @Test
    void 昨日は日記だけあっても昨日の区画を出す() throws Exception {
        when(diaryService.findDiaryForSummary(USER_ID, YESTERDAY))
                .thenReturn(Optional.of(new DiaryItem(YESTERDAY, "日記の本文", LocalTime.of(23, 0))));

        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(containsString("id=\"yesterday\"")))
                .andExpect(content().string(containsString("日記あり（23:00 に作成）")));
    }

    @Test
    void フラッシュメッセージがあれば赤で表示する() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)).flashAttr("errorMessage", PostController.POST_NOT_FOUND_MESSAGE))
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("つぶやきが見つかりませんでした")));
    }

    @Test
    void フラッシュメッセージがなければ出さない() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL)))
                .andExpect(content().string(not(containsString("role=\"alert\""))));
    }

    @Test
    void 今日は1回だけ求め日付を確かめ直す取得は使わない() throws Exception {
        mockMvc.perform(get("/").with(user(PRINCIPAL))).andExpect(status().isOk());

        verify(postService, times(1)).today();
        verify(diaryService, never()).findDiary(any(), any());
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
        verify(postService).post(USER_ID, "  寝坊した  ");
    }

    @Test
    void 改行はブラウザが送ったCRLFのまま受け取る() throws Exception {
        mockMvc.perform(post("/posts")
                        .param("content", "朝\r\n昼")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));

        verify(postService).post(USER_ID, "朝\r\n昼");
    }

    @Test
    void 改行を含む100文字はCRLFを2文字と数えるのでエラーになる() throws Exception {
        // 画面の maxlength・残り文字数は改行を 1 文字と数えるが、サーバーはブラウザが送る CRLF を 2 文字と数える
        var content = "あ".repeat(49) + "\r\n" + "い".repeat(50);

        mockMvc.perform(post("/posts")
                        .param("content", content)
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("100 文字以内で入力してください")));

        verify(postService, never()).post(anyLong(), anyString());
    }

    @Test
    void 空白と改行だけならエラーを表示して投稿しない() throws Exception {
        mockMvc.perform(post("/posts")
                        .param("content", " 　\r\n ")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("内容を入力してください")));

        verify(postService, never()).post(anyLong(), anyString());
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

        verify(postService, never()).post(anyLong(), anyString());
    }

    @Test
    void CSRFトークンなしの投稿は403() throws Exception {
        mockMvc.perform(post("/posts").param("content", "寝坊した").with(user(PRINCIPAL)))
                .andExpect(status().isForbidden());

        verify(postService, never()).post(anyLong(), anyString());
    }

    // --- 編集モード ---

    @Test
    void 編集モードではその行だけ編集フォームにする() throws Exception {
        when(postService.getEditablePost(USER_ID, 10L)).thenReturn(TODAY_POST);

        mockMvc.perform(get("/posts/10/edit").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("action=\"/posts/10/edit\"")))
                .andExpect(content().string(containsString("action=\"/posts/10/delete\"")))
                .andExpect(content().string(containsString("投稿時刻は変わりません")))
                .andExpect(content().string(containsString("/posts/11/edit")))
                .andExpect(content().string(not(containsString("action=\"/posts/11/delete\""))));
    }

    @Test
    void 昨日のつぶやきの編集モードは昨日の区画の行を編集フォームにする() throws Exception {
        when(postService.findPosts(USER_ID, YESTERDAY)).thenReturn(List.of(YESTERDAY_POST));
        when(postService.getEditablePost(USER_ID, 20L)).thenReturn(YESTERDAY_POST);

        var html = mockMvc.perform(get("/posts/20/edit").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var yesterdaySection = html.substring(html.indexOf("id=\"yesterday\""));
        assertThat(yesterdaySection)
                .contains("action=\"/posts/20/edit\"")
                .contains("action=\"/posts/20/delete\"")
                .contains("夜ふかしした");
        assertThat(html.substring(0, html.indexOf("id=\"yesterday\""))).doesNotContain("action=\"/posts/20/edit\"");
    }

    @Test
    void 他人や一昨日のつぶやきの編集モードはメッセージを付けてトップへリダイレクト() throws Exception {
        when(postService.getEditablePost(USER_ID, 99L)).thenThrow(new ResourceNotFoundException("Post", 99L));

        mockMvc.perform(get("/posts/99/edit").with(user(PRINCIPAL)))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("errorMessage", PostController.POST_NOT_FOUND_MESSAGE));
    }

    // --- 編集 ---

    @Test
    void 編集するとトップへリダイレクト() throws Exception {
        mockMvc.perform(post("/posts/10/edit")
                        .param("content", " 昼のカレーがおいしかった ")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));

        verify(postService).edit(USER_ID, 10L, " 昼のカレーがおいしかった ");
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

        verify(postService, never()).edit(anyLong(), anyLong(), anyString());
    }

    @Test
    void 他人のつぶやきの編集はメッセージを付けてトップへリダイレクト() throws Exception {
        doThrow(new ResourceNotFoundException("Post", 99L)).when(postService).edit(USER_ID, 99L, "書き換え");

        mockMvc.perform(post("/posts/99/edit")
                        .param("content", "書き換え")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("errorMessage", PostController.POST_NOT_FOUND_MESSAGE));
    }

    @Test
    void 他人のつぶやきは入力エラーでも再表示せずにトップへリダイレクト() throws Exception {
        when(postService.getEditablePost(USER_ID, 99L)).thenThrow(new ResourceNotFoundException("Post", 99L));

        mockMvc.perform(post("/posts/99/edit")
                        .param("content", "")
                        .with(user(PRINCIPAL))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("errorMessage", PostController.POST_NOT_FOUND_MESSAGE));
    }

    @Test
    void CSRFトークンなしの編集は403() throws Exception {
        mockMvc.perform(post("/posts/10/edit").param("content", "書き換え").with(user(PRINCIPAL)))
                .andExpect(status().isForbidden());

        verify(postService, never()).edit(anyLong(), anyLong(), any());
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
                .andExpect(flash().attribute("errorMessage", PostController.POST_NOT_FOUND_MESSAGE));
    }

    @Test
    void CSRFトークンなしの削除は403() throws Exception {
        mockMvc.perform(post("/posts/10/delete").with(user(PRINCIPAL))).andExpect(status().isForbidden());

        verify(postService, never()).delete(anyLong(), anyLong());
    }
}
