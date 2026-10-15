package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long POST_ID = 10L;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostCategoryClassifier categoryClassifier;

    // 保存の部分（TransactionTemplate の中）を実際に動かすため、トランザクションマネージャーだけをモックにする
    @Mock
    private PlatformTransactionManager transactionManager;

    private final User user = new User("demo", "hash", Instant.EPOCH);

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        lenient().when(postRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(categoryClassifier.classify(any())).thenReturn(Optional.empty());
    }

    private PostService serviceAt(String jstDateTime) {
        var clock = Clock.fixed(jst(jstDateTime), PostService.ZONE);
        return new PostService(
                postRepository, userRepository, categoryClassifier, new TransactionTemplate(transactionManager), clock);
    }

    private static Instant jst(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(PostService.ZONE).toInstant();
    }

    @Test
    void today_isJstDate() {
        // UTC ではまだ 10/7 だが、日本時間では 10/8
        assertThat(serviceAt("2026-10-08T00:00:00").today()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(serviceAt("2026-10-08T23:59:59").today()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void isEditableDate_onlyTodayAndYesterday() {
        var service = serviceAt("2026-10-08T00:00:00");

        assertThat(service.isEditableDate(LocalDate.of(2026, 10, 8))).isTrue();
        assertThat(service.isEditableDate(LocalDate.of(2026, 10, 7))).isTrue();
        assertThat(service.isEditableDate(LocalDate.of(2026, 10, 6))).isFalse();
        assertThat(service.isEditableDate(LocalDate.of(2026, 10, 9))).isFalse();
    }

    @Test
    void findTodayPosts_searchesTodayInJst() {
        // UTC ではまだ 10/7 だが、日本時間では 10/8 の 0:30
        var service = serviceAt("2026-10-08T00:30:00");
        var post = new Post(user, "おはよう", jst("2026-10-08T00:10:00"));
        when(postRepository.findByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 8), PostService.ZONE))
                .thenReturn(List.of(post));

        assertThat(service.findTodayPosts(USER_ID)).containsExactly(post);
    }

    @Test
    void findTodayPosts_callsRepositoryWithTodayRange() {
        // given
        var clock = Clock.fixed(Instant.parse("2026-05-23T01:00:00Z"), ZoneId.of("Asia/Tokyo"));
        var postService = new PostService(
                postRepository, userRepository, categoryClassifier, new TransactionTemplate(transactionManager), clock);
        var userId = 1L;
        var zone = clock.getZone();
        var today = LocalDate.now(clock);
        var user = new User("testuser", "encoded", Instant.now(clock));
        List<Post> expected = List.of(new Post(user, "テスト", Instant.now(clock)));
        // 日付から時刻の範囲への変換は Repository の default メソッドが行う。Mockito のモックでは default メソッドの本体が
        // 実行されないので、内側のクエリではなく、サービスが実際に呼ぶ findByUserIdAndDate をモックする
        when(postRepository.findByUserIdAndDate(userId, today, zone)).thenReturn(expected);

        // when
        List<Post> result = postService.findTodayPosts(userId);

        // then
        assertThat(result).hasSize(1);
        verify(postRepository).findByUserIdAndDate(userId, today, zone);
    }

    @Test
    void findPostsByDate_passesAnyDateAndClockZone() {
        // 今日・昨日に限らず、任意の日付のつぶやきを取れる
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "先週のランチ", jst("2026-10-01T12:00:00"));
        when(postRepository.findByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 1), PostService.ZONE))
                .thenReturn(List.of(post));

        assertThat(service.findPostsByDate(USER_ID, LocalDate.of(2026, 10, 1))).containsExactly(post);
    }

    @Test
    void findPosts_searchesFromJstMidnightToNextMidnight() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "おはよう", jst("2026-10-08T07:30:00"));
        when(postRepository.findByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 8), PostService.ZONE))
                .thenReturn(List.of(post));

        var items = service.findPosts(USER_ID, LocalDate.of(2026, 10, 8));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.content()).isEqualTo("おはよう");
            assertThat(item.postedTime()).isEqualTo(LocalTime.of(7, 30));
            assertThat(item.edited()).isFalse();
        });
    }

    @Test
    void create_savesStrippedContentWithSamePostedAndUpdatedTime() {
        var service = serviceAt("2026-10-08T12:00:00");

        service.create(USER_ID, "  ランチ\n");

        var captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getContent()).isEqualTo("ランチ");
        assertThat(saved.getPostedAt()).isEqualTo(jst("2026-10-08T12:00:00"));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getPostedAt());
        assertThat(saved.isEdited()).isFalse();
    }

    @Test
    void create_assignsTheClassifiedCategory() {
        var service = serviceAt("2026-10-08T12:00:00");
        when(categoryClassifier.classify("会議の資料をまとめた")).thenReturn(Optional.of(PostCategory.WORK));

        service.create(USER_ID, " 会議の資料をまとめた ");

        var captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(PostCategory.WORK);
        assertThat(captor.getValue().isCategorySetByUser()).isFalse();
    }

    @Test
    void create_savesPostAndClassifiesCategory() {
        // given
        var clock = Clock.fixed(Instant.parse("2026-05-23T01:00:00Z"), ZoneId.of("Asia/Tokyo"));
        // 原稿のコンストラクタ（引数 4 つ）に、保存だけをトランザクションにするための TransactionTemplate を足している
        var postService = new PostService(
                postRepository, userRepository, categoryClassifier, new TransactionTemplate(transactionManager), clock);
        var userId = 1L;
        var content = "ミーティングが3件あった";
        var user = new User("testuser", "encoded", Instant.now(clock));
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(categoryClassifier.classify(content)).thenReturn(Optional.of(PostCategory.WORK));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Post result = postService.create(userId, content);

        // then
        assertThat(result.getCategory()).isEqualTo(PostCategory.WORK);
        verify(categoryClassifier).classify(content);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void create_keepsOtherWhenClassificationFails() {
        // 判定できなくても、つぶやきは保存する（コアループを Claude API の不調で止めない）
        var service = serviceAt("2026-10-08T12:00:00");
        when(categoryClassifier.classify("ランチ")).thenReturn(Optional.empty());

        service.create(USER_ID, "ランチ");

        var captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(PostCategory.OTHER);
    }

    @Test
    void create_allowsUpToTheDailyLimit() {
        // UTC ではまだ 10/7 だが、日本時間では 10/8 の 0:30。日本時間の今日の件数で数える
        var service = serviceAt("2026-10-08T00:30:00");
        when(postRepository.countByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 8), PostService.ZONE))
                .thenReturn((long) PostService.DAILY_POST_LIMIT - 1);

        service.create(USER_ID, "16 件目");

        verify(postRepository).save(any());
    }

    @Test
    void create_rejectsWhenDailyLimitReached_withoutCallingClaude() {
        var service = serviceAt("2026-10-08T12:00:00");
        when(postRepository.countByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 8), PostService.ZONE))
                .thenReturn((long) PostService.DAILY_POST_LIMIT);

        assertThatThrownBy(() -> service.create(USER_ID, "17 件目"))
                .isInstanceOf(DailyPostLimitExceededException.class)
                .hasMessage("Daily post limit exceeded: userId=1, limit=16");

        verify(categoryClassifier, never()).classify(any());
        verify(postRepository, never()).save(any());
    }

    @Test
    void create_rejectsWhenLimitIsReachedWhileClassifying() {
        // 判定を待つ間に、別のタブから 16 件目が投稿された
        var service = serviceAt("2026-10-08T12:00:00");
        when(postRepository.countByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 8), PostService.ZONE))
                .thenReturn((long) PostService.DAILY_POST_LIMIT - 1, (long) PostService.DAILY_POST_LIMIT);

        assertThatThrownBy(() -> service.create(USER_ID, "17 件目")).isInstanceOf(DailyPostLimitExceededException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void edit_reclassifiesWhenContentChanges() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        post.assignCategory(PostCategory.PRIVATE);
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));
        when(categoryClassifier.classify("資格の勉強をした")).thenReturn(Optional.of(PostCategory.LEARNING));

        service.edit(USER_ID, POST_ID, "資格の勉強をした", post.getCategory());

        assertThat(post.getCategory()).isEqualTo(PostCategory.LEARNING);
    }

    @Test
    void edit_keepsPreviousCategoryWhenClassificationFails() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        post.assignCategory(PostCategory.PRIVATE);
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));
        when(categoryClassifier.classify("カレー")).thenReturn(Optional.empty());

        service.edit(USER_ID, POST_ID, "カレー", post.getCategory());

        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
        assertThat(post.getContent()).isEqualTo("カレー");
    }

    @Test
    void edit_doesNotClassifyWhenContentIsUnchanged() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.edit(USER_ID, POST_ID, " ランチ ", post.getCategory());

        verify(categoryClassifier, never()).classify(any());
    }

    @Test
    void edit_doesNotClassifyCategorySetByUser() {
        // 手で直したカテゴリは、本文を編集しても AI に上書きさせない（Post ではなく service で守る）
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        post.assignCategory(PostCategory.WORK);
        post.markCategorySetByUser();
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.edit(USER_ID, POST_ID, "取引先とランチ", post.getCategory());

        verify(categoryClassifier, never()).classify(any());
        assertThat(post.getCategory()).isEqualTo(PostCategory.WORK);
        assertThat(post.getContent()).isEqualTo("取引先とランチ");
    }

    @Test
    void edit_doesNotOverwriteCategorySetByUserWhileClassifying() {
        // 判定を待つ間に手で直された場合（トランザクションの中で読み直したつぶやきに印が立っている）
        var service = serviceAt("2026-10-08T12:00:00");
        var before = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        var reloaded = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        reloaded.assignCategory(PostCategory.WORK);
        reloaded.markCategorySetByUser();
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(reloaded));
        when(categoryClassifier.classify("取引先とランチ")).thenReturn(Optional.of(PostCategory.PRIVATE));

        service.edit(USER_ID, POST_ID, "取引先とランチ", before.getCategory());

        assertThat(reloaded.getCategory()).isEqualTo(PostCategory.WORK);
    }

    @Test
    void edit_categoryChangedByUser_isMarkedAndNotClassified() {
        // 本文とカテゴリを同じリクエストで直したら、AI の判定より手で選んだカテゴリを優先する
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.edit(USER_ID, POST_ID, "取引先とランチ", PostCategory.WORK);

        verify(categoryClassifier, never()).classify(any());
        assertThat(post.getCategory()).isEqualTo(PostCategory.WORK);
        assertThat(post.isCategorySetByUser()).isTrue();
        assertThat(post.getContent()).isEqualTo("取引先とランチ");
    }

    @Test
    void edit_categoryOnly_doesNotMarkEdited() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.edit(USER_ID, POST_ID, " ランチ ", PostCategory.PRIVATE);

        verify(categoryClassifier, never()).classify(any());
        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
        assertThat(post.isCategorySetByUser()).isTrue();
        assertThat(post.getUpdatedAt()).isEqualTo(jst("2026-10-08T11:00:00"));
        assertThat(post.isEdited()).isFalse();
    }

    @Test
    void edit_sameCategory_isNotTreatedAsChangedByUser() {
        // 編集フォームは今のカテゴリを選んだ状態で送られてくるので、同じ値なら手で直したことにしない
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));
        when(categoryClassifier.classify("資格の勉強をした")).thenReturn(Optional.of(PostCategory.LEARNING));

        service.edit(USER_ID, POST_ID, "資格の勉強をした", PostCategory.OTHER);

        assertThat(post.getCategory()).isEqualTo(PostCategory.LEARNING);
        assertThat(post.isCategorySetByUser()).isFalse();
    }

    @Test
    void createAndEdit_runOutsideTransactionsBecauseTheyCallTheClaudeApi() throws NoSuchMethodException {
        for (var method : List.of(
                PostService.class.getMethod("create", Long.class, String.class),
                PostService.class.getMethod("edit", Long.class, Long.class, String.class, PostCategory.class))) {
            assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
        }
    }

    @Test
    void create_classifiesOutsideTheTransactionAndSavesInside() {
        // Claude API の応答を待つ間に DB 接続を握らない：判定 → トランザクション開始 → 保存 → コミット の順
        var service = serviceAt("2026-10-08T12:00:00");

        service.create(USER_ID, "会議の資料をまとめた");

        var order = inOrder(categoryClassifier, transactionManager, postRepository);
        order.verify(categoryClassifier).classify("会議の資料をまとめた");
        order.verify(transactionManager).getTransaction(any());
        order.verify(postRepository).save(any());
        order.verify(transactionManager).commit(any());
    }

    @Test
    void create_checksTheLimitAgainstTheSameDateInsideTheTransaction() {
        // 上限の確かめ直しは、最初に求めた「今日」で行う（判定を待つ間に 0 時をまたいでも、投稿時刻の日付で数える）
        var service = serviceAt("2026-10-08T23:59:59");

        service.create(USER_ID, "おやすみ");

        verify(postRepository, times(2)).countByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 8), PostService.ZONE);
    }

    @Test
    void edit_classifiesOutsideTheTransactionAndSavesInside() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.edit(USER_ID, POST_ID, "資格の勉強をした", post.getCategory());

        var order = inOrder(categoryClassifier, transactionManager);
        order.verify(categoryClassifier).classify("資格の勉強をした");
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
    }

    @Test
    void edit_othersPost_throwsNotFoundWithoutClassifying() {
        // 他人のつぶやきは、存在しない場合と区別しない（IDOR 対策）
        var service = serviceAt("2026-10-08T12:00:00");
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, "書き換え", PostCategory.OTHER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryClassifier, never()).classify(any());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void edit_postDeletedWhileClassifying_throwsNotFoundAndRollsBack() {
        // 判定を待つ間に、別のタブから削除された場合（トランザクションの中で読み直すと見つからない）
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(post))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, "取引先とランチ", post.getCategory()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void getEditablePost_returnsTodaysPostAsItem() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:30:00"));
        post.assignCategory(PostCategory.PRIVATE);
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        var item = service.getEditablePost(USER_ID, POST_ID);

        assertThat(item.content()).isEqualTo("ランチ");
        assertThat(item.postedTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(item.category()).isEqualTo(PostCategory.PRIVATE);
        assertThat(item.edited()).isFalse();
    }

    @Test
    void getEditablePost_othersPostOrDayBeforeYesterday_throwsNotFound() {
        var service = serviceAt("2026-10-08T12:00:00");
        when(postRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(new Post(user, "おととい", jst("2026-10-06T23:59:59"))));

        assertThatThrownBy(() -> service.getEditablePost(USER_ID, 99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getEditablePost(USER_ID, POST_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void countPosts_passesDateAndClockZone() {
        var service = serviceAt("2026-10-08T12:00:00");
        when(postRepository.countByUserIdAndDate(USER_ID, LocalDate.of(2026, 10, 1), PostService.ZONE))
                .thenReturn(5L);

        assertThat(service.countPosts(USER_ID, LocalDate.of(2026, 10, 1))).isEqualTo(5L);
    }

    @Test
    void edit_keepsPostedAtAndMarksEdited() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.edit(USER_ID, POST_ID, " カレー ", post.getCategory());

        assertThat(post.getContent()).isEqualTo("カレー");
        assertThat(post.getPostedAt()).isEqualTo(jst("2026-10-08T11:00:00"));
        assertThat(post.getUpdatedAt()).isEqualTo(jst("2026-10-08T12:00:00"));
        assertThat(post.isEdited()).isTrue();
    }

    @Test
    void edit_yesterdaysPostJustAfterMidnight() {
        var service = serviceAt("2026-10-08T00:00:30");
        var post = new Post(user, "おやすみ", jst("2026-10-07T23:59:59"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.edit(USER_ID, POST_ID, "おやすみなさい", post.getCategory());

        assertThat(post.getContent()).isEqualTo("おやすみなさい");
    }

    @Test
    void edit_postFromDayBeforeYesterday_throwsNotFound() {
        var service = serviceAt("2026-10-08T00:00:30");
        var post = new Post(user, "おやすみ", jst("2026-10-06T23:59:59"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, "直したい", PostCategory.OTHER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(post.getContent()).isEqualTo("おやすみ");
    }

    @Test
    void updateCategory_assignsAndMarksWithoutMarkingEdited() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.updateCategory(USER_ID, POST_ID, PostCategory.WORK);

        verify(categoryClassifier, never()).classify(any());
        assertThat(post.getCategory()).isEqualTo(PostCategory.WORK);
        assertThat(post.isCategorySetByUser()).isTrue();
        assertThat(post.getContent()).isEqualTo("ランチ");
        assertThat(post.isEdited()).isFalse();
    }

    @Test
    void updateCategory_postBeforeYesterday_canBeChanged() {
        // 本文の編集と違い、カテゴリは昨日より前のつぶやきでも直せる
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-01T12:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.updateCategory(USER_ID, POST_ID, PostCategory.PRIVATE);

        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
    }

    @Test
    void updateCategory_othersPost_throwsNotFound() {
        var service = serviceAt("2026-10-08T12:00:00");
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCategory(USER_ID, POST_ID, PostCategory.WORK))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_postFromDayBeforeYesterday_throwsNotFound() {
        // 本文の編集と同じく、消せるのは今日と昨日のつぶやきだけ
        var service = serviceAt("2026-10-08T00:00:30");
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(new Post(user, "おやすみ", jst("2026-10-06T23:59:59"))));

        assertThatThrownBy(() -> service.delete(USER_ID, POST_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(postRepository, never()).delete(any());
    }

    @Test
    void delete_othersPost_throwsNotFound() {
        var service = serviceAt("2026-10-08T12:00:00");
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, POST_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(postRepository, never()).delete(any());
    }

    @Test
    void delete_ownPost() {
        var service = serviceAt("2026-10-08T12:00:00");
        var post = new Post(user, "ランチ", jst("2026-10-08T11:00:00"));
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));

        service.delete(USER_ID, POST_ID);

        verify(postRepository).delete(post);
    }
}
