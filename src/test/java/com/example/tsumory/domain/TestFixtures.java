package com.example.tsumory.domain;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

/**
 * テストで使う共通のデータ。ユーザー・つぶやき・日記と、日本時間の時刻・固定の時計を作る。
 *
 * <p>本文は、実際に使われそうな日本語のつぶやき・日記にする（「テスト」「日記」のような中身のない文字列にしない）。時刻は
 * {@code "2026-10-08T12:00:00"} のような日本時間の文字列で指定する。
 */
public final class TestFixtures {

    public static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    /** テストの基準の「今」。2026-10-08（木）12:00（日本時間）。 */
    public static final String NOW = "2026-10-08T12:00:00";

    /** {@link #NOW} の日付。 */
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    /** 利用者「花子」の登録日時。2026-10-01 0:00（日本時間）ちょうど。UTC ではまだ 9/30。 */
    public static final String REGISTERED_AT = "2026-10-01T00:00:00";

    // --- つぶやきの本文（カテゴリの判定で使い分けやすいよう、話題ごとに用意する） ---

    /** プライベート：朝の出来事。 */
    public static final String OVERSLEPT = "寝坊して、朝ごはんを食べずに家を出た";

    /** プライベート：昼ごはん。 */
    public static final String LUNCH = "昼は駅前の定食屋で唐揚げ定食";

    /** 仕事：会議。 */
    public static final String MEETING = "午後の定例会議が1時間も延びた";

    /** 仕事：取引先との昼ごはん（プライベートか仕事か、AI の判定が分かれやすい）。 */
    public static final String LUNCH_WITH_CLIENT = "取引先の佐藤さんとランチしながら打ち合わせ";

    /** 学習：勉強。 */
    public static final String STUDY = "通勤電車で英単語アプリを30分続けた";

    /** プライベート：夜の出来事。 */
    public static final String BAKERY = "帰りにパン屋で塩バターパンを買った";

    /** プライベート：寝る前のひとこと。 */
    public static final String GOOD_NIGHT = "明日も早いので、そろそろ寝る";

    /** 日記の本文。上のつぶやきから書いたような内容。 */
    public static final String DIARY_CONTENT = """
            寝坊して、朝ごはんも食べずに慌ただしく家を出た。午後の定例会議は1時間も延びて、正直くたくただった。
            それでも帰りにパン屋で買った塩バターパンがおいしくて、少しだけ元気が出た。明日はもう少し早く起きたい。""";

    /** 一括生成で順に使い回す、本文とカテゴリの組。カテゴリは本文の話題に合わせる（仕事の話題は「仕事」、勉強は「学習」）。 */
    private record SamplePost(String content, PostCategory category) {}

    private static final List<SamplePost> SAMPLE_POSTS = List.of(
            new SamplePost(OVERSLEPT, PostCategory.PRIVATE),
            new SamplePost(STUDY, PostCategory.LEARNING),
            new SamplePost(MEETING, PostCategory.WORK),
            new SamplePost(LUNCH, PostCategory.PRIVATE),
            new SamplePost(LUNCH_WITH_CLIENT, PostCategory.WORK),
            new SamplePost(BAKERY, PostCategory.PRIVATE),
            new SamplePost(GOOD_NIGHT, PostCategory.PRIVATE));

    private TestFixtures() {}

    /** 日本時間の日時の文字列（{@code "2026-10-08T12:00:00"}）を {@link Instant} にする。 */
    public static Instant jst(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(JST).toInstant();
    }

    /** 日本時間のその日時で止まった時計。 */
    public static Clock clockAt(String jstDateTime) {
        return Clock.fixed(jst(jstDateTime), JST);
    }

    /** 利用者「花子」。登録日は {@link #REGISTERED_AT}。パスワードは BCrypt のハッシュ値の形にしてある（平文は持たない）。 */
    public static User hanako() {
        return new User("hanako", "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy", jst(REGISTERED_AT));
    }

    /** {@code postedAt}（日本時間）に投稿したつぶやき。カテゴリは初期値の「その他」。 */
    public static Post post(User user, String content, String postedAt) {
        return new Post(user, content, jst(postedAt));
    }

    /** {@code postedAt}（日本時間）に投稿し、AI がカテゴリを判定したつぶやき。 */
    public static Post post(User user, String content, String postedAt, PostCategory category) {
        var post = post(user, content, postedAt);
        post.assignCategory(category);
        return post;
    }

    /** その日の日記。{@code generatedAt}（日本時間）に書いた。 */
    public static Diary diary(User user, LocalDate date, String content, String generatedAt) {
        return new Diary(user, date, content, jst(generatedAt));
    }

    // --- 大量データの一括生成 ---
    // 中身を見ない場面はモック（mockPosts）、中身を検証する場面は実体（posts・diaries）を使い分ける

    /**
     * {@code count} 件のつぶやきのモック。件数を数えるだけ、受け取ったリストをそのまま渡すだけなど、中身（本文・時刻・カテゴリ）を読まない
     * テストで使う。
     *
     * <p>モックの {@code getContent()} などは {@code null} を返すので、中身を読む処理（検索結果の表示用への詰め替えなど）には渡さない。
     * その場合は {@link #posts} で実体を作る。
     */
    public static List<Post> mockPosts(int count) {
        return IntStream.range(0, count).mapToObj(i -> mock(Post.class)).toList();
    }

    /**
     * {@code count} 件のつぶやきの実体。中身を検証するテスト（検索結果の上限など、サービスが本文や時刻を読む場面）で使う。投稿時刻は
     * {@code firstPostedAt}（日本時間）から 1 分ずつ後ろにずらす（古い順に並ぶ）。
     *
     * <p>本文はサンプルを順に使い回し、カテゴリは本文の話題に合わせて付ける。
     */
    public static List<Post> posts(User user, int count, String firstPostedAt) {
        var first = jst(firstPostedAt);
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    var sample = SAMPLE_POSTS.get(i % SAMPLE_POSTS.size());
                    var post = new Post(user, sample.content(), first.plusSeconds(60L * i));
                    post.assignCategory(sample.category());
                    return post;
                })
                .toList();
    }

    /**
     * ASCII の文字列を、画面に表示されないタグ文字（U+E0000 + 文字コード）に置き換える。画面では見えないまま AI には読める指示を、
     * 本文に隠す手口（ASCII smuggling）を再現する。
     */
    public static String hiddenAscii(String ascii) {
        var hidden = new StringBuilder();
        ascii.chars().forEach(c -> hidden.appendCodePoint(0xE0000 + c));
        return hidden.toString();
    }

    /** {@code count} 件の日記。日付は {@code firstDate} から 1 日ずつ（古い順に並ぶ）。どれもその日の 21:30 に書いた。 */
    public static List<Diary> diaries(User user, int count, LocalDate firstDate) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    var date = firstDate.plusDays(i);
                    return diary(user, date, DIARY_CONTENT, date + "T21:30:00");
                })
                .toList();
    }
}
