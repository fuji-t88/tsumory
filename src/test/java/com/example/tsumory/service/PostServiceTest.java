package com.example.tsumory.service;

import static com.example.tsumory.domain.TestData.BAKERY;
import static com.example.tsumory.domain.TestData.GOOD_NIGHT;
import static com.example.tsumory.domain.TestData.LUNCH;
import static com.example.tsumory.domain.TestData.LUNCH_WITH_CLIENT;
import static com.example.tsumory.domain.TestData.MEETING;
import static com.example.tsumory.domain.TestData.NOW;
import static com.example.tsumory.domain.TestData.OVERSLEPT;
import static com.example.tsumory.domain.TestData.STUDY;
import static com.example.tsumory.domain.TestData.TODAY;
import static com.example.tsumory.domain.TestData.clockAt;
import static com.example.tsumory.domain.TestData.hanako;
import static com.example.tsumory.domain.TestData.jst;
import static com.example.tsumory.domain.TestData.mockPosts;
import static com.example.tsumory.domain.TestData.post;
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
import java.time.LocalDate;
import java.time.LocalTime;
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

    /** 今日（{@link com.example.tsumory.domain.TestData#TODAY}）の 11:00 に投稿したつぶやきの時刻。 */
    private static final String THIS_MORNING = "2026-10-08T11:00:00";

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostCategoryClassifier categoryClassifier;

    // 保存の部分（TransactionTemplate の中）を実際に動かすため、トランザクションマネージャーだけをモックにする
    @Mock
    private PlatformTransactionManager transactionManager;

    private final User user = hanako();

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        lenient().when(postRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(categoryClassifier.classify(any())).thenReturn(Optional.empty());
    }

    /** 日本時間のその日時で止まった時計で動く PostService。 */
    private PostService serviceAt(String jstDateTime) {
        return service(clockAt(jstDateTime));
    }

    // 原稿のコンストラクタ（引数 4 つ）に、保存だけをトランザクションにするための TransactionTemplate を足している
    private PostService service(Clock clock) {
        return new PostService(
                postRepository, userRepository, categoryClassifier, new TransactionTemplate(transactionManager), clock);
    }

    /** 今日の午前に投稿した、カテゴリ「その他」の昼ごはんのつぶやき。編集・カテゴリ修正の出発点にする。 */
    private Post todaysLunch() {
        return post(user, LUNCH, THIS_MORNING);
    }

    private void stubFind(Post post) {
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.of(post));
    }

    private Post capturedSavedPost() {
        var captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        return captor.getValue();
    }

    // --- 今日・昨日 ---

    @Test
    void today_isJstDate() {
        // UTC ではまだ 10/7 だが、日本時間では 10/8
        assertThat(serviceAt("2026-10-08T00:00:00").today()).isEqualTo(TODAY);
        assertThat(serviceAt("2026-10-08T23:59:59").today()).isEqualTo(TODAY);
    }

    @Test
    void isEditableDate_onlyTodayAndYesterday() {
        var service = serviceAt("2026-10-08T00:00:00");

        assertThat(service.isEditableDate(TODAY)).isTrue();
        assertThat(service.isEditableDate(TODAY.minusDays(1))).isTrue();
        assertThat(service.isEditableDate(TODAY.minusDays(2))).isFalse();
        assertThat(service.isEditableDate(TODAY.plusDays(1))).isFalse();
    }

    // --- 取得 ---

    @Test
    void findTodayPosts_searchesTodayInJst() {
        // UTC ではまだ 10/7 だが、日本時間では 10/8 の 0:30
        var service = serviceAt("2026-10-08T00:30:00");
        var post = post(user, GOOD_NIGHT, "2026-10-08T00:10:00");
        when(postRepository.findByUserIdAndDate(USER_ID, TODAY, PostService.ZONE))
                .thenReturn(List.of(post));

        assertThat(service.findTodayPosts(USER_ID)).containsExactly(post);
    }

    @Test
    void findTodayPosts_callsRepositoryWithTodayRange() {
        // given
        var clock = clockAt(NOW);
        var postService = service(clock);
        var zone = clock.getZone();
        var today = LocalDate.now(clock);
        // サービスはリポジトリの結果をそのまま返すだけで中身を読まないので、実体ではなくモックで十分
        List<Post> expected = mockPosts(100);
        // 日付から時刻の範囲への変換は Repository の default メソッドが行う。Mockito のモックでは default メソッドの本体が
        // 実行されないので、内側のクエリではなく、サービスが実際に呼ぶ findByUserIdAndDate をモックする
        when(postRepository.findByUserIdAndDate(USER_ID, today, zone)).thenReturn(expected);

        // when
        List<Post> result = postService.findTodayPosts(USER_ID);

        // then
        assertThat(result).hasSize(100);
        verify(postRepository).findByUserIdAndDate(USER_ID, today, zone);
    }

    @Test
    void findPostsByDate_passesAnyDateAndClockZone() {
        // 今日・昨日に限らず、任意の日付のつぶやきを取れる
        var service = serviceAt(NOW);
        var lastWeek = LocalDate.of(2026, 10, 1);
        var post = post(user, LUNCH, "2026-10-01T12:00:00");
        when(postRepository.findByUserIdAndDate(USER_ID, lastWeek, PostService.ZONE))
                .thenReturn(List.of(post));

        assertThat(service.findPostsByDate(USER_ID, lastWeek)).containsExactly(post);
    }

    @Test
    void findPosts_searchesFromJstMidnightToNextMidnight() {
        var service = serviceAt(NOW);
        when(postRepository.findByUserIdAndDate(USER_ID, TODAY, PostService.ZONE))
                .thenReturn(List.of(post(user, OVERSLEPT, "2026-10-08T07:30:00")));

        var items = service.findPosts(USER_ID, TODAY);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.content()).isEqualTo(OVERSLEPT);
            assertThat(item.postedTime()).isEqualTo(LocalTime.of(7, 30));
            assertThat(item.edited()).isFalse();
        });
    }

    @Test
    void countPosts_passesDateAndClockZone() {
        var service = serviceAt(NOW);
        var lastWeek = LocalDate.of(2026, 10, 1);
        when(postRepository.countByUserIdAndDate(USER_ID, lastWeek, PostService.ZONE))
                .thenReturn(5L);

        assertThat(service.countPosts(USER_ID, lastWeek)).isEqualTo(5L);
    }

    @Test
    void getEditablePost_returnsTodaysPostAsItem() {
        var service = serviceAt(NOW);
        stubFind(post(user, LUNCH, "2026-10-08T11:30:00", PostCategory.PRIVATE));

        var item = service.getEditablePost(USER_ID, POST_ID);

        assertThat(item.content()).isEqualTo(LUNCH);
        assertThat(item.postedTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(item.category()).isEqualTo(PostCategory.PRIVATE);
        assertThat(item.edited()).isFalse();
    }

    @Test
    void getEditablePost_othersPostOrDayBeforeYesterday_throwsNotFound() {
        var service = serviceAt(NOW);
        when(postRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());
        stubFind(post(user, GOOD_NIGHT, "2026-10-06T23:59:59"));

        assertThatThrownBy(() -> service.getEditablePost(USER_ID, 99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getEditablePost(USER_ID, POST_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- 投稿 ---

    @Test
    void create_savesStrippedContentWithSamePostedAndUpdatedTime() {
        var service = serviceAt(NOW);

        service.create(USER_ID, "  " + LUNCH + "\n");

        var saved = capturedSavedPost();
        assertThat(saved.getContent()).isEqualTo(LUNCH);
        assertThat(saved.getPostedAt()).isEqualTo(jst(NOW));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getPostedAt());
        assertThat(saved.isEdited()).isFalse();
    }

    @Test
    void create_assignsTheClassifiedCategory() {
        var service = serviceAt(NOW);
        when(categoryClassifier.classify(MEETING)).thenReturn(Optional.of(PostCategory.WORK));

        service.create(USER_ID, " " + MEETING + " ");

        var saved = capturedSavedPost();
        assertThat(saved.getCategory()).isEqualTo(PostCategory.WORK);
        assertThat(saved.isCategorySetByUser()).isFalse();
    }

    @Test
    void create_savesPostAndClassifiesCategory() {
        // given
        var postService = serviceAt(NOW);
        when(categoryClassifier.classify(MEETING)).thenReturn(Optional.of(PostCategory.WORK));

        // when
        Post result = postService.create(USER_ID, MEETING);

        // then
        assertThat(result.getCategory()).isEqualTo(PostCategory.WORK);
        verify(categoryClassifier).classify(MEETING);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void create_keepsOtherWhenClassificationFails() {
        // 判定できなくても、つぶやきは保存する（コアループを Claude API の不調で止めない）
        var service = serviceAt(NOW);
        when(categoryClassifier.classify(LUNCH)).thenReturn(Optional.empty());

        service.create(USER_ID, LUNCH);

        assertThat(capturedSavedPost().getCategory()).isEqualTo(PostCategory.OTHER);
    }

    @Test
    void create_allowsUpToTheDailyLimit() {
        // UTC ではまだ 10/7 だが、日本時間では 10/8 の 0:30。日本時間の今日の件数で数える
        var service = serviceAt("2026-10-08T00:30:00");
        when(postRepository.countByUserIdAndDate(USER_ID, TODAY, PostService.ZONE))
                .thenReturn((long) PostService.DAILY_POST_LIMIT - 1);

        service.create(USER_ID, GOOD_NIGHT);

        verify(postRepository).save(any());
    }

    @Test
    void create_rejectsWhenDailyLimitReached_withoutCallingClaude() {
        var service = serviceAt(NOW);
        when(postRepository.countByUserIdAndDate(USER_ID, TODAY, PostService.ZONE))
                .thenReturn((long) PostService.DAILY_POST_LIMIT);

        assertThatThrownBy(() -> service.create(USER_ID, BAKERY))
                .isInstanceOf(DailyPostLimitExceededException.class)
                .hasMessage("Daily post limit exceeded: userId=1, limit=16");

        verify(categoryClassifier, never()).classify(any());
        verify(postRepository, never()).save(any());
    }

    @Test
    void create_rejectsWhenLimitIsReachedWhileClassifying() {
        // 判定を待つ間に、別のタブから 16 件目が投稿された
        var service = serviceAt(NOW);
        when(postRepository.countByUserIdAndDate(USER_ID, TODAY, PostService.ZONE))
                .thenReturn((long) PostService.DAILY_POST_LIMIT - 1, (long) PostService.DAILY_POST_LIMIT);

        assertThatThrownBy(() -> service.create(USER_ID, BAKERY)).isInstanceOf(DailyPostLimitExceededException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void create_classifiesOutsideTheTransactionAndSavesInside() {
        // Claude API の応答を待つ間に DB 接続を握らない：判定 → トランザクション開始 → 保存 → コミット の順
        var service = serviceAt(NOW);

        service.create(USER_ID, MEETING);

        var order = inOrder(categoryClassifier, transactionManager, postRepository);
        order.verify(categoryClassifier).classify(MEETING);
        order.verify(transactionManager).getTransaction(any());
        order.verify(postRepository).save(any());
        order.verify(transactionManager).commit(any());
    }

    @Test
    void create_checksTheLimitAgainstTheSameDateInsideTheTransaction() {
        // 上限の確かめ直しは、最初に求めた「今日」で行う（判定を待つ間に 0 時をまたいでも、投稿時刻の日付で数える）
        var service = serviceAt("2026-10-08T23:59:59");

        service.create(USER_ID, GOOD_NIGHT);

        verify(postRepository, times(2)).countByUserIdAndDate(USER_ID, TODAY, PostService.ZONE);
    }

    @Test
    void createAndEdit_runOutsideTransactionsBecauseTheyCallTheClaudeApi() throws NoSuchMethodException {
        for (var method : List.of(
                PostService.class.getMethod("create", Long.class, String.class),
                PostService.class.getMethod("edit", Long.class, Long.class, String.class, PostCategory.class))) {
            assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
        }
    }

    // --- 編集 ---

    @Test
    void edit_reclassifiesWhenContentChanges() {
        var service = serviceAt(NOW);
        var post = post(user, LUNCH, THIS_MORNING, PostCategory.PRIVATE);
        stubFind(post);
        when(categoryClassifier.classify(STUDY)).thenReturn(Optional.of(PostCategory.LEARNING));

        service.edit(USER_ID, POST_ID, STUDY, post.getCategory());

        assertThat(post.getCategory()).isEqualTo(PostCategory.LEARNING);
    }

    @Test
    void edit_keepsPreviousCategoryWhenClassificationFails() {
        var service = serviceAt(NOW);
        var post = post(user, LUNCH, THIS_MORNING, PostCategory.PRIVATE);
        stubFind(post);
        when(categoryClassifier.classify(BAKERY)).thenReturn(Optional.empty());

        service.edit(USER_ID, POST_ID, BAKERY, post.getCategory());

        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
        assertThat(post.getContent()).isEqualTo(BAKERY);
    }

    @Test
    void edit_doesNotClassifyWhenContentIsUnchanged() {
        var service = serviceAt(NOW);
        var post = todaysLunch();
        stubFind(post);

        service.edit(USER_ID, POST_ID, " " + LUNCH + " ", post.getCategory());

        verify(categoryClassifier, never()).classify(any());
    }

    @Test
    void edit_doesNotClassifyCategorySetByUser() {
        // 手で直したカテゴリは、本文を編集しても AI に上書きさせない（Post ではなく service で守る）
        var service = serviceAt(NOW);
        var post = post(user, LUNCH_WITH_CLIENT, THIS_MORNING, PostCategory.WORK);
        post.markCategorySetByUser();
        stubFind(post);

        service.edit(USER_ID, POST_ID, MEETING, post.getCategory());

        verify(categoryClassifier, never()).classify(any());
        assertThat(post.getCategory()).isEqualTo(PostCategory.WORK);
        assertThat(post.getContent()).isEqualTo(MEETING);
    }

    @Test
    void edit_doesNotOverwriteCategorySetByUserWhileClassifying() {
        // 判定を待つ間に手で直された場合（トランザクションの中で読み直したつぶやきに印が立っている）
        // 取引先とのランチを「仕事」に直している間に、本文を勉強の話題に書き換えた。AI は「学習」と判定するが、手で直した「仕事」を優先する
        var service = serviceAt(NOW);
        var before = post(user, LUNCH_WITH_CLIENT, THIS_MORNING);
        var reloaded = post(user, LUNCH_WITH_CLIENT, THIS_MORNING, PostCategory.WORK);
        reloaded.markCategorySetByUser();
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(reloaded));
        when(categoryClassifier.classify(STUDY)).thenReturn(Optional.of(PostCategory.LEARNING));

        service.edit(USER_ID, POST_ID, STUDY, before.getCategory());

        assertThat(reloaded.getCategory()).isEqualTo(PostCategory.WORK);
    }

    @Test
    void edit_categoryChangedByUser_isMarkedAndNotClassified() {
        // 本文とカテゴリを同じリクエストで直したら、AI の判定より手で選んだカテゴリを優先する
        var service = serviceAt(NOW);
        var post = todaysLunch();
        stubFind(post);

        service.edit(USER_ID, POST_ID, LUNCH_WITH_CLIENT, PostCategory.WORK);

        verify(categoryClassifier, never()).classify(any());
        assertThat(post.getCategory()).isEqualTo(PostCategory.WORK);
        assertThat(post.isCategorySetByUser()).isTrue();
        assertThat(post.getContent()).isEqualTo(LUNCH_WITH_CLIENT);
    }

    @Test
    void edit_categoryOnly_doesNotMarkEdited() {
        var service = serviceAt(NOW);
        var post = todaysLunch();
        stubFind(post);

        service.edit(USER_ID, POST_ID, " " + LUNCH + " ", PostCategory.PRIVATE);

        verify(categoryClassifier, never()).classify(any());
        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
        assertThat(post.isCategorySetByUser()).isTrue();
        assertThat(post.getUpdatedAt()).isEqualTo(jst(THIS_MORNING));
        assertThat(post.isEdited()).isFalse();
    }

    @Test
    void edit_sameCategory_isNotTreatedAsChangedByUser() {
        // 編集フォームは今のカテゴリを選んだ状態で送られてくるので、同じ値なら手で直したことにしない
        var service = serviceAt(NOW);
        var post = todaysLunch();
        stubFind(post);
        when(categoryClassifier.classify(STUDY)).thenReturn(Optional.of(PostCategory.LEARNING));

        service.edit(USER_ID, POST_ID, STUDY, PostCategory.OTHER);

        assertThat(post.getCategory()).isEqualTo(PostCategory.LEARNING);
        assertThat(post.isCategorySetByUser()).isFalse();
    }

    @Test
    void edit_classifiesOutsideTheTransactionAndSavesInside() {
        var service = serviceAt(NOW);
        var post = todaysLunch();
        stubFind(post);

        service.edit(USER_ID, POST_ID, STUDY, post.getCategory());

        var order = inOrder(categoryClassifier, transactionManager);
        order.verify(categoryClassifier).classify(STUDY);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
    }

    @Test
    void edit_othersPost_throwsNotFoundWithoutClassifying() {
        // 他人のつぶやきは、存在しない場合と区別しない（IDOR 対策）
        var service = serviceAt(NOW);
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, BAKERY, PostCategory.OTHER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryClassifier, never()).classify(any());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void edit_postDeletedWhileClassifying_throwsNotFoundAndRollsBack() {
        // 判定を待つ間に、別のタブから削除された場合（トランザクションの中で読み直すと見つからない）
        var service = serviceAt(NOW);
        var post = todaysLunch();
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(post))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, LUNCH_WITH_CLIENT, post.getCategory()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void edit_keepsPostedAtAndMarksEdited() {
        var service = serviceAt(NOW);
        var post = todaysLunch();
        stubFind(post);

        service.edit(USER_ID, POST_ID, " " + BAKERY + " ", post.getCategory());

        assertThat(post.getContent()).isEqualTo(BAKERY);
        assertThat(post.getPostedAt()).isEqualTo(jst(THIS_MORNING));
        assertThat(post.getUpdatedAt()).isEqualTo(jst(NOW));
        assertThat(post.isEdited()).isTrue();
    }

    @Test
    void edit_yesterdaysPostJustAfterMidnight() {
        var service = serviceAt("2026-10-08T00:00:30");
        var post = post(user, GOOD_NIGHT, "2026-10-07T23:59:59");
        stubFind(post);

        service.edit(USER_ID, POST_ID, "明日も早いので、もう寝よう", post.getCategory());

        assertThat(post.getContent()).isEqualTo("明日も早いので、もう寝よう");
    }

    @Test
    void edit_postFromDayBeforeYesterday_throwsNotFound() {
        var service = serviceAt("2026-10-08T00:00:30");
        var post = post(user, GOOD_NIGHT, "2026-10-06T23:59:59");
        stubFind(post);

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, BAKERY, PostCategory.OTHER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(post.getContent()).isEqualTo(GOOD_NIGHT);
    }

    // --- カテゴリだけの修正 ---

    @Test
    void updateCategory_assignsAndMarksWithoutMarkingEdited() {
        // AI が判定できず「その他」になった取引先とのランチを、手で「仕事」に直す
        var service = serviceAt(NOW);
        var post = post(user, LUNCH_WITH_CLIENT, THIS_MORNING);
        stubFind(post);

        service.updateCategory(USER_ID, POST_ID, PostCategory.WORK);

        verify(categoryClassifier, never()).classify(any());
        assertThat(post.getCategory()).isEqualTo(PostCategory.WORK);
        assertThat(post.isCategorySetByUser()).isTrue();
        assertThat(post.getContent()).isEqualTo(LUNCH_WITH_CLIENT);
        assertThat(post.isEdited()).isFalse();
    }

    @Test
    void updateCategory_postBeforeYesterday_canBeChanged() {
        // 本文の編集と違い、カテゴリは昨日より前のつぶやきでも直せる
        var service = serviceAt(NOW);
        var post = post(user, LUNCH, "2026-10-01T12:00:00");
        stubFind(post);

        service.updateCategory(USER_ID, POST_ID, PostCategory.PRIVATE);

        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
    }

    @Test
    void updateCategory_updatesOwnPost() {
        // given
        var postService = serviceAt(NOW);
        var post = post(user, LUNCH_WITH_CLIENT, NOW);
        stubFind(post);

        // when
        postService.updateCategory(USER_ID, POST_ID, PostCategory.WORK);

        // then
        assertThat(post.getCategory()).isEqualTo(PostCategory.WORK);
    }

    @Test
    void updateCategory_throwsForOtherUsersPost() {
        // given
        var postService = serviceAt(NOW);
        // 削除と同じガード：持ち主を条件に取得し、見つからなければ専用の例外（他人のつぶやきと存在しないつぶやきを区別しない）
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.updateCategory(USER_ID, POST_ID, PostCategory.WORK))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- 削除 ---

    @Test
    void delete_deletesOwnPost() {
        // given
        var postService = serviceAt(NOW);
        var post = post(user, BAKERY, NOW);
        stubFind(post);

        // when
        postService.delete(USER_ID, POST_ID);

        // then
        verify(postRepository).delete(post);
    }

    @Test
    void delete_throwsForOtherUsersPost() {
        // given
        var postService = serviceAt(NOW);
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.delete(USER_ID, POST_ID)).isInstanceOf(ResourceNotFoundException.class);
        // 例外が投げられたことだけでなく、実際に削除されなかったことまで確かめる（ガードにバグがあっても削除が通ってしまうのを見逃さない）
        verify(postRepository, never()).delete(any());
    }

    @Test
    void delete_postFromDayBeforeYesterday_throwsNotFound() {
        // 本文の編集と同じく、消せるのは今日と昨日のつぶやきだけ
        var service = serviceAt("2026-10-08T00:00:30");
        stubFind(post(user, GOOD_NIGHT, "2026-10-06T23:59:59"));

        assertThatThrownBy(() -> service.delete(USER_ID, POST_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(postRepository, never()).delete(any());
    }
}
