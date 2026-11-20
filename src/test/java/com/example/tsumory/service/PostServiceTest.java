package com.example.tsumory.service;

import static com.example.tsumory.domain.TestFixtures.BAKERY;
import static com.example.tsumory.domain.TestFixtures.GOOD_NIGHT;
import static com.example.tsumory.domain.TestFixtures.JST;
import static com.example.tsumory.domain.TestFixtures.LUNCH;
import static com.example.tsumory.domain.TestFixtures.LUNCH_WITH_CLIENT;
import static com.example.tsumory.domain.TestFixtures.MEETING;
import static com.example.tsumory.domain.TestFixtures.NOW;
import static com.example.tsumory.domain.TestFixtures.OVERSLEPT;
import static com.example.tsumory.domain.TestFixtures.STUDY;
import static com.example.tsumory.domain.TestFixtures.TODAY;
import static com.example.tsumory.domain.TestFixtures.clockAt;
import static com.example.tsumory.domain.TestFixtures.hanako;
import static com.example.tsumory.domain.TestFixtures.jpegBytes;
import static com.example.tsumory.domain.TestFixtures.jst;
import static com.example.tsumory.domain.TestFixtures.pngBytes;
import static com.example.tsumory.domain.TestFixtures.post;
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
import com.example.tsumory.domain.PostPhoto;
import com.example.tsumory.domain.User;
import com.example.tsumory.repository.PostPhotoRepository;
import com.example.tsumory.repository.PostRepository;
import com.example.tsumory.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long POST_ID = 10L;

    /** 今日（{@link com.example.tsumory.domain.TestFixtures#TODAY}）の 11:00 に投稿したつぶやきの時刻。 */
    private static final String THIS_MORNING = "2026-10-08T11:00:00";

    /** 写真を付けない投稿。 */
    private static final PhotoUpload NO_PHOTO = null;

    private static final PhotoUpload JPEG = new PhotoUpload(jpegBytes(4, 3));

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostPhotoRepository postPhotoRepository;

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
                postRepository,
                postPhotoRepository,
                userRepository,
                categoryClassifier,
                new TransactionTemplate(transactionManager),
                clock);
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
    void 今日は日本時間の日付() {
        // UTC ではまだ 10/7 だが、日本時間では 10/8
        assertThat(serviceAt("2026-10-08T00:00:00").today()).isEqualTo(TODAY);
        assertThat(serviceAt("2026-10-08T23:59:59").today()).isEqualTo(TODAY);
    }

    @Test
    void 今日は時計のタイムゾーンで決まる() {
        // タイムゾーンの基準は Clock だけ。同じ瞬間でも、UTC の時計ならまだ 10/7（日本時間に決め打ちしない）
        var instant = jst("2026-10-08T00:30:00");

        assertThat(service(Clock.fixed(instant, ZoneOffset.UTC)).today()).isEqualTo(TODAY.minusDays(1));
        assertThat(service(Clock.fixed(instant, JST)).today()).isEqualTo(TODAY);
    }

    @Test
    void 編集できる日付は今日と昨日だけ() {
        var service = serviceAt("2026-10-08T00:00:00");

        assertThat(service.isEditableDate(TODAY)).isTrue();
        assertThat(service.isEditableDate(TODAY.minusDays(1))).isTrue();
        assertThat(service.isEditableDate(TODAY.minusDays(2))).isFalse();
        assertThat(service.isEditableDate(TODAY.plusDays(1))).isFalse();
    }

    // --- 取得 ---

    @Test
    void 今日や昨日に限らず任意の日付のつぶやきを時計のタイムゾーンで探す() {
        // 今日・昨日に限らず、任意の日付のつぶやきを取れる
        var service = serviceAt(NOW);
        var lastWeek = LocalDate.of(2026, 10, 1);
        var post = post(user, LUNCH, "2026-10-01T12:00:00");
        when(postRepository.findByUserIdAndDate(USER_ID, lastWeek, JST)).thenReturn(List.of(post));

        assertThat(service.findPostsByDate(USER_ID, lastWeek)).containsExactly(post);
    }

    @Test
    void その日のつぶやきを画面に出す形にして返す() {
        var service = serviceAt(NOW);
        when(postRepository.findByUserIdAndDate(USER_ID, TODAY, JST))
                .thenReturn(List.of(post(user, OVERSLEPT, "2026-10-08T07:30:00")));

        var items = service.findPosts(USER_ID, TODAY);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.content()).isEqualTo(OVERSLEPT);
            assertThat(item.postedTime()).isEqualTo(LocalTime.of(7, 30));
            assertThat(item.edited()).isFalse();
        });
    }

    @Test
    void つぶやきの件数を日付と時計のタイムゾーンで数える() {
        var service = serviceAt(NOW);
        var lastWeek = LocalDate.of(2026, 10, 1);
        when(postRepository.countByUserIdAndDate(USER_ID, lastWeek, JST)).thenReturn(5L);

        assertThat(service.countPosts(USER_ID, lastWeek)).isEqualTo(5L);
    }

    @Test
    void 今日のつぶやきは編集モードで開ける() {
        var service = serviceAt(NOW);
        stubFind(post(user, LUNCH, "2026-10-08T11:30:00", PostCategory.PRIVATE));

        var item = service.getEditablePost(USER_ID, POST_ID);

        assertThat(item.content()).isEqualTo(LUNCH);
        assertThat(item.postedTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(item.category()).isEqualTo(PostCategory.PRIVATE);
        assertThat(item.edited()).isFalse();
    }

    @Test
    void 他人のつぶやきや一昨日のつぶやきは編集モードで開けない() {
        var service = serviceAt(NOW);
        when(postRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());
        stubFind(post(user, GOOD_NIGHT, "2026-10-06T23:59:59"));

        assertThatThrownBy(() -> service.getEditablePost(USER_ID, 99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getEditablePost(USER_ID, POST_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- 投稿 ---

    @Test
    void 投稿すると前後の空白を除いた本文を投稿時刻と更新時刻を同じにして保存する() {
        var service = serviceAt(NOW);

        service.create(USER_ID, "  " + LUNCH + "\n", NO_PHOTO);

        var saved = capturedSavedPost();
        assertThat(saved.getContent()).isEqualTo(LUNCH);
        assertThat(saved.getPostedAt()).isEqualTo(jst(NOW));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getPostedAt());
        assertThat(saved.isEdited()).isFalse();
    }

    @Test
    void 投稿すると判定したカテゴリが入り手で直した印は付かない() {
        var service = serviceAt(NOW);
        when(categoryClassifier.classify(MEETING)).thenReturn(Optional.of(PostCategory.WORK));

        service.create(USER_ID, " " + MEETING + " ", NO_PHOTO);

        var saved = capturedSavedPost();
        assertThat(saved.getCategory()).isEqualTo(PostCategory.WORK);
        assertThat(saved.isCategorySetByUser()).isFalse();
    }

    @Test
    void 投稿すると判定したカテゴリの入ったつぶやきを返す() {
        // given
        var postService = serviceAt(NOW);
        when(categoryClassifier.classify(MEETING)).thenReturn(Optional.of(PostCategory.WORK));

        // when
        Post result = postService.create(USER_ID, MEETING, NO_PHOTO);

        // then
        assertThat(result.getCategory()).isEqualTo(PostCategory.WORK);
        verify(categoryClassifier).classify(MEETING);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void カテゴリを判定できなくてもその他のまま投稿できる() {
        // 判定できなくても、つぶやきは保存する（コアループを Claude API の不調で止めない）
        var service = serviceAt(NOW);
        when(categoryClassifier.classify(LUNCH)).thenReturn(Optional.empty());

        service.create(USER_ID, LUNCH, NO_PHOTO);

        assertThat(capturedSavedPost().getCategory()).isEqualTo(PostCategory.OTHER);
    }

    @Test
    void 上限の件数に達した時点で上限到達になる() {
        var service = serviceAt("2026-10-08T12:00:00");
        when(postRepository.countByUserIdAndDate(USER_ID, TODAY, JST))
                .thenReturn((long) PostService.DAILY_POST_LIMIT - 1)
                .thenReturn((long) PostService.DAILY_POST_LIMIT);

        assertThat(service.isPostLimitReached(USER_ID, TODAY)).isFalse();
        assertThat(service.isPostLimitReached(USER_ID, TODAY)).isTrue();
    }

    @Test
    void 上限の件数までは1日に投稿できる() {
        // UTC ではまだ 10/7 だが、日本時間では 10/8 の 0:30。日本時間の今日の件数で数える
        var service = serviceAt("2026-10-08T00:30:00");
        when(postRepository.countByUserIdAndDate(USER_ID, TODAY, JST))
                .thenReturn((long) PostService.DAILY_POST_LIMIT - 1);

        service.create(USER_ID, GOOD_NIGHT, NO_PHOTO);

        verify(postRepository).save(any());
    }

    @Test
    void 上限に達していたらClaudeを呼ばずに投稿を断る() {
        var service = serviceAt(NOW);
        when(postRepository.countByUserIdAndDate(USER_ID, TODAY, JST)).thenReturn((long) PostService.DAILY_POST_LIMIT);

        assertThatThrownBy(() -> service.create(USER_ID, BAKERY, NO_PHOTO))
                .isInstanceOf(DailyPostLimitExceededException.class)
                .hasMessage("Daily post limit exceeded: userId=1, limit=16");

        verify(categoryClassifier, never()).classify(any());
        verify(postRepository, never()).save(any());
    }

    @Test
    void カテゴリの判定を待つ間に上限に達したら投稿を断る() {
        // 判定を待つ間に、別のタブから 16 件目が投稿された
        var service = serviceAt(NOW);
        when(postRepository.countByUserIdAndDate(USER_ID, TODAY, JST))
                .thenReturn((long) PostService.DAILY_POST_LIMIT - 1, (long) PostService.DAILY_POST_LIMIT);

        assertThatThrownBy(() -> service.create(USER_ID, BAKERY, NO_PHOTO))
                .isInstanceOf(DailyPostLimitExceededException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void 投稿の上限はカテゴリの判定より前に短いトランザクションで確かめる() {
        // 判定の前に読む上限の確認も、短いトランザクションで済ませる。トランザクションの外で直接読むと、その接続を
        // メソッドが終わるまで（Claude API の応答を待つ間も）握り続けるため
        var service = serviceAt(NOW);

        service.create(USER_ID, MEETING, NO_PHOTO);

        var order = inOrder(transactionManager, postRepository, categoryClassifier);
        order.verify(transactionManager).getTransaction(any());
        order.verify(postRepository).countByUserIdAndDate(USER_ID, TODAY, JST);
        order.verify(transactionManager).commit(any());
        order.verify(categoryClassifier).classify(MEETING);
    }

    @Test
    void 投稿ではカテゴリをトランザクションの外で判定し中で保存する() {
        // Claude API の応答を待つ間に DB 接続を握らない：判定 → トランザクション開始 → 保存 → コミット の順
        var service = serviceAt(NOW);

        service.create(USER_ID, MEETING, NO_PHOTO);

        var order = inOrder(categoryClassifier, transactionManager, postRepository);
        order.verify(categoryClassifier).classify(MEETING);
        order.verify(transactionManager).getTransaction(any());
        order.verify(postRepository).save(any());
        order.verify(transactionManager).commit(any());
    }

    @Test
    void 保存するトランザクションの中でも同じ日付で上限を確かめる() {
        // 上限の確かめ直しは、最初に求めた「今日」で行う（判定を待つ間に 0 時をまたいでも、投稿時刻の日付で数える）
        var service = serviceAt("2026-10-08T23:59:59");

        service.create(USER_ID, GOOD_NIGHT, NO_PHOTO);

        verify(postRepository, times(2)).countByUserIdAndDate(USER_ID, TODAY, JST);
    }

    // --- 写真の添付 ---

    @Test
    void 写真を付けて投稿するとつぶやきと一緒に写真も保存する() {
        var service = serviceAt(NOW);

        var saved = service.create(USER_ID, LUNCH, JPEG);

        var captor = ArgumentCaptor.forClass(PostPhoto.class);
        verify(postPhotoRepository).save(captor.capture());
        var photo = captor.getValue();
        assertThat(photo.getPost()).isSameAs(saved);
        assertThat(photo.getContentType()).isEqualTo("image/jpeg");
        assertThat(photo.getData()).containsExactly(JPEG.data());
        assertThat(photo.getCreatedAt()).isEqualTo(jst(NOW));
    }

    @Test
    void 写真を付けずに投稿すると写真は保存しない() {
        serviceAt(NOW).create(USER_ID, LUNCH, NO_PHOTO);

        verify(postRepository).save(any());
        verify(postPhotoRepository, never()).save(any());
    }

    @Test
    void 写真はつぶやきと同じトランザクションの中で保存する() {
        // 上限の確認（読むだけ）と保存の 2 つだけがトランザクション。写真のために別のトランザクションを張らない
        var service = serviceAt(NOW);

        service.create(USER_ID, LUNCH, JPEG);

        var order = inOrder(categoryClassifier, transactionManager, postRepository, postPhotoRepository);
        order.verify(categoryClassifier).classify(LUNCH);
        order.verify(transactionManager).getTransaction(any());
        order.verify(postRepository).save(any());
        order.verify(postPhotoRepository).save(any());
        order.verify(transactionManager).commit(any());
        verify(transactionManager, times(2)).getTransaction(any());
    }

    @Test
    void 写真の保存に失敗したらつぶやきの保存も取り消す() {
        var service = serviceAt(NOW);
        when(postPhotoRepository.save(any())).thenThrow(new IllegalStateException("photo save failed"));

        assertThatThrownBy(() -> service.create(USER_ID, LUNCH, JPEG)).isInstanceOf(IllegalStateException.class);

        var order = inOrder(postRepository, postPhotoRepository, transactionManager);
        order.verify(postRepository).save(any());
        order.verify(postPhotoRepository).save(any());
        order.verify(transactionManager).rollback(any());
        // コミットしたのは、判定の前に上限を確かめた読むだけのトランザクションだけ
        verify(transactionManager, times(1)).commit(any());
    }

    @Test
    void 保存する写真の形式はブラウザが送った形式ではなくデータから決める() {
        serviceAt(NOW).create(USER_ID, LUNCH, new PhotoUpload(pngBytes(4, 3)));

        var captor = ArgumentCaptor.forClass(PostPhoto.class);
        verify(postPhotoRepository).save(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("image/png");
    }

    @Test
    void 受け付けない写真ならClaudeを呼ばず上限も数えず何も保存しない() {
        // 写真の検証は、上限の確認と Claude API より前に行う（料金をかけないため）
        var service = serviceAt(NOW);
        var text = new PhotoUpload("昼はカレーだった".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.create(USER_ID, LUNCH, text))
                .isInstanceOfSatisfying(
                        InvalidPhotoException.class,
                        e -> assertThat(e.getReason()).isEqualTo(InvalidPhotoException.Reason.UNSUPPORTED_TYPE));

        verify(postRepository, never()).countByUserIdAndDate(any(), any(), any());
        verify(categoryClassifier, never()).classify(any());
        verify(postRepository, never()).save(any());
        verify(postPhotoRepository, never()).save(any());
    }

    @Test
    void 上限に達していたら写真も保存しない() {
        var service = serviceAt(NOW);
        when(postRepository.countByUserIdAndDate(USER_ID, TODAY, JST)).thenReturn((long) PostService.DAILY_POST_LIMIT);

        assertThatThrownBy(() -> service.create(USER_ID, BAKERY, JPEG))
                .isInstanceOf(DailyPostLimitExceededException.class);

        verify(postPhotoRepository, never()).save(any());
    }

    @Test
    void 投稿と編集はClaudeのAPIを呼ぶのでトランザクションを張らない() throws NoSuchMethodException {
        for (var method : List.of(
                PostService.class.getMethod("create", Long.class, String.class, PhotoUpload.class),
                PostService.class.getMethod("edit", Long.class, Long.class, String.class, PostCategory.class))) {
            assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
        }
    }

    // --- 編集 ---

    @Test
    void 本文を変えるとカテゴリを判定し直す() {
        var service = serviceAt(NOW);
        var post = post(user, LUNCH, THIS_MORNING, PostCategory.PRIVATE);
        stubFind(post);
        when(categoryClassifier.classify(STUDY)).thenReturn(Optional.of(PostCategory.LEARNING));

        service.edit(USER_ID, POST_ID, STUDY, post.getCategory());

        assertThat(post.getCategory()).isEqualTo(PostCategory.LEARNING);
    }

    @Test
    void 判定し直せなければ前のカテゴリのまま() {
        var service = serviceAt(NOW);
        var post = post(user, LUNCH, THIS_MORNING, PostCategory.PRIVATE);
        stubFind(post);
        when(categoryClassifier.classify(BAKERY)).thenReturn(Optional.empty());

        service.edit(USER_ID, POST_ID, BAKERY, post.getCategory());

        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
        assertThat(post.getContent()).isEqualTo(BAKERY);
    }

    @Test
    void 本文が変わらなければカテゴリを判定しない() {
        var service = serviceAt(NOW);
        var post = todaysLunch();
        stubFind(post);

        service.edit(USER_ID, POST_ID, " " + LUNCH + " ", post.getCategory());

        verify(categoryClassifier, never()).classify(any());
    }

    @Test
    void 手で直したカテゴリは判定し直さない() {
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
    void 判定を待つ間に手で直されたカテゴリは上書きしない() {
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
    void 編集でカテゴリを変えると手で直した印が付き判定しない() {
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
    void カテゴリだけを変えても編集済みにしない() {
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
    void 今と同じカテゴリが送られても手で直したことにしない() {
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
    void 編集ではカテゴリの判定より前に短いトランザクションでつぶやきを読む() {
        // create と同じく、判定の前の読み込みも短いトランザクションで済ませ、Claude API を待つ間に DB 接続を握らない
        var service = serviceAt(NOW);
        stubFind(todaysLunch());

        service.edit(USER_ID, POST_ID, STUDY, PostCategory.OTHER);

        var order = inOrder(transactionManager, postRepository, categoryClassifier);
        order.verify(transactionManager).getTransaction(any());
        order.verify(postRepository).findByIdAndUserId(POST_ID, USER_ID);
        order.verify(transactionManager).commit(any());
        order.verify(categoryClassifier).classify(STUDY);
    }

    @Test
    void 編集ではカテゴリをトランザクションの外で判定し中で保存する() {
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
    void 他人のつぶやきは判定せずに見つからないとして断る() {
        // 他人のつぶやきは、存在しない場合と区別しない（IDOR 対策）
        var service = serviceAt(NOW);
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, BAKERY, PostCategory.OTHER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryClassifier, never()).classify(any());
        // 読み込みの短いトランザクションはロールバックし、保存のトランザクションは始めない
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void 判定を待つ間に削除されたつぶやきは見つからないとして断りロールバックする() {
        // 判定を待つ間に、別のタブから削除された場合（トランザクションの中で読み直すと見つからない）
        var service = serviceAt(NOW);
        var post = todaysLunch();
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID))
                .thenReturn(Optional.of(post))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, LUNCH_WITH_CLIENT, post.getCategory()))
                .isInstanceOf(ResourceNotFoundException.class);
        // 判定の前の読み込み（1 つ目のトランザクション）はコミットし、保存（2 つ目）はロールバックする
        verify(transactionManager, times(1)).commit(any());
        verify(transactionManager, times(1)).rollback(any());
    }

    @Test
    void 編集しても投稿時刻は変えず編集済みになる() {
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
    void 日付が変わった直後でも昨日のつぶやきは編集できる() {
        var service = serviceAt("2026-10-08T00:00:30");
        var post = post(user, GOOD_NIGHT, "2026-10-07T23:59:59");
        stubFind(post);

        service.edit(USER_ID, POST_ID, "明日も早いので、もう寝よう", post.getCategory());

        assertThat(post.getContent()).isEqualTo("明日も早いので、もう寝よう");
    }

    @Test
    void 一昨日のつぶやきは編集できない() {
        var service = serviceAt("2026-10-08T00:00:30");
        var post = post(user, GOOD_NIGHT, "2026-10-06T23:59:59");
        stubFind(post);

        assertThatThrownBy(() -> service.edit(USER_ID, POST_ID, BAKERY, PostCategory.OTHER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(post.getContent()).isEqualTo(GOOD_NIGHT);
    }

    // --- カテゴリだけの修正 ---

    @Test
    void カテゴリだけを直すと手で直した印が付き編集済みにはならない() {
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
    void 一昨日より前のつぶやきもカテゴリは直せる() {
        // 本文の編集と違い、カテゴリは昨日より前のつぶやきでも直せる
        var service = serviceAt(NOW);
        var post = post(user, LUNCH, "2026-10-01T12:00:00");
        stubFind(post);

        service.updateCategory(USER_ID, POST_ID, PostCategory.PRIVATE);

        assertThat(post.getCategory()).isEqualTo(PostCategory.PRIVATE);
    }

    @Test
    void 自分のつぶやきのカテゴリを直せる() {
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
    void 他人のつぶやきのカテゴリは直せない() {
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
    void 自分のつぶやきを削除できる() {
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
    void 他人のつぶやきは削除できない() {
        // given
        var postService = serviceAt(NOW);
        when(postRepository.findByIdAndUserId(POST_ID, USER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.delete(USER_ID, POST_ID)).isInstanceOf(ResourceNotFoundException.class);
        // 例外が投げられたことだけでなく、実際に削除されなかったことまで確かめる（ガードにバグがあっても削除が通ってしまうのを見逃さない）
        verify(postRepository, never()).delete(any());
    }

    @Test
    void 一昨日のつぶやきは削除できない() {
        // 本文の編集と同じく、消せるのは今日と昨日のつぶやきだけ
        var service = serviceAt("2026-10-08T00:00:30");
        stubFind(post(user, GOOD_NIGHT, "2026-10-06T23:59:59"));

        assertThatThrownBy(() -> service.delete(USER_ID, POST_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(postRepository, never()).delete(any());
    }

    // --- 検索 ---

    private static final Limit LIMIT = Limit.of(51);

    @Test
    void キーワードとカテゴリの有無で検索のクエリを選ぶ() {
        var service = serviceAt("2026-10-08T12:00:00");

        service.searchPosts(USER_ID, "カレー", null, LIMIT);
        service.searchPosts(USER_ID, "カレー", PostCategory.WORK, LIMIT);
        service.searchPosts(USER_ID, "", PostCategory.LEARNING, LIMIT);

        verify(postRepository).searchByKeyword(USER_ID, "カレー", LIMIT);
        verify(postRepository).searchByKeywordAndCategory(USER_ID, "カレー", PostCategory.WORK, LIMIT);
        verify(postRepository).searchByCategory(USER_ID, PostCategory.LEARNING, LIMIT);
    }

    @Test
    void 検索のキーワードはLIKE用にエスケープして渡す() {
        serviceAt("2026-10-08T12:00:00").searchPosts(USER_ID, "達成率100%!", null, LIMIT);

        verify(postRepository).searchByKeyword(USER_ID, "達成率100!%!!", LIMIT);
    }
}
