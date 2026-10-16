package com.example.tsumory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    /**
     * 本文の上限（文字数）。フォーム（{@code PostForm}・{@code PostEditForm}）の {@code @Size}、列の長さ、画面の {@code maxlength}
     * （{@code InputLimitsAdvice} が渡す）がこの値を使う。変えるときは、DB の列（{@code VARCHAR(100)}）もマイグレーションで変える。
     */
    public static final int CONTENT_MAX_LENGTH = 100;

    /**
     * 本文に入れられない「見えない文字」1 文字を表す正規表現の文字クラス。改行（CR・LF。別の決まりで拒否する）以外の制御文字（Cc）、
     * 書式文字（Cf。ゼロ幅スペース、文字の向きを変える制御文字、タグ文字など）、行・段落の区切り（Zl・Zp）。
     *
     * <p>画面では無害に見える本文に、日記のプロンプトへの指示を隠せないようにするため（タグ文字で ASCII を見えない形に書く手口など）。
     * ゼロ幅接合子（U+200D）は絵文字をつなぐ（👨‍👩‍👧 など）ので許す。全角スペース・異体字セレクタは別の種類なので対象外。
     */
    public static final String INVISIBLE_CHAR_CLASS = "[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}&&[^\\r\\n\\u200D]]";

    /** フォームの {@code @Pattern} 用：{@link #INVISIBLE_CHAR_CLASS} の文字を 1 つも含まない（改行の有無は別の {@code @Pattern} で見る）。 */
    public static final String NO_INVISIBLE_CHARS_REGEX = "(?:(?!" + INVISIBLE_CHAR_CLASS + ")[\\s\\S])*";

    private static final Pattern INVISIBLE_CHAR = Pattern.compile(INVISIBLE_CHAR_CLASS);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant postedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // DB の既定値は INSERT で列を省いたときだけ効く。JPA は値を明示して INSERT するので、初期値で OTHER を持たせる
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = PostCategory.NAME_MAX_LENGTH)
    private PostCategory category = PostCategory.OTHER;

    /** ユーザーが手でカテゴリを直したら true。true のあいだは AI の判定で上書きしない（判断は service が行う）。 */
    @Column(nullable = false)
    private boolean categorySetByUser = false;

    /**
     * @throws NullPointerException 値が {@code null} のとき
     * @throws IllegalArgumentException 本文が空・{@link #CONTENT_MAX_LENGTH} 文字を超える・改行や見えない文字（{@link #INVISIBLE_CHAR_CLASS}）を含むとき
     */
    public Post(User user, String content, Instant now) {
        this.user = Objects.requireNonNull(user, "user");
        this.content = requireValidContent(content);
        this.postedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /**
     * 本文と更新時刻を変える。投稿時刻とカテゴリは変えない（判定し直すまでは前のカテゴリのまま）。
     *
     * @throws NullPointerException 値が {@code null} のとき
     * @throws IllegalArgumentException 本文が空・{@link #CONTENT_MAX_LENGTH} 文字を超える・改行や見えない文字（{@link #INVISIBLE_CHAR_CLASS}）を含むとき
     */
    public void edit(String content, Instant now) {
        Objects.requireNonNull(now, "now");
        this.content = requireValidContent(content);
        this.updatedAt = now;
    }

    /**
     * カテゴリを割り当てる。AI の判定を反映するときと、ユーザーが手で直すときの両方で使う。更新時刻は変えない（カテゴリを直しても「編集済み」にしない）。
     *
     * <p>誰が呼んだかは区別しない。手で直したカテゴリを AI に上書きさせないのは、呼び出す service の役目（{@link #isCategorySetByUser()} を見る）。
     */
    public void assignCategory(PostCategory category) {
        this.category = Objects.requireNonNull(category, "category");
    }

    /** ユーザーが手でカテゴリを直したことを記録する。手で直す service が {@link #assignCategory} と一緒に呼ぶ。 */
    public void markCategorySetByUser() {
        this.categorySetByUser = true;
    }

    public boolean isEdited() {
        return updatedAt.isAfter(postedAt);
    }

    /**
     * 本文の決まり（空でない、{@link #CONTENT_MAX_LENGTH} 文字以内、見えない文字なし、改行なし）を守る。フォーム（{@code PostForm}・{@code PostEditForm}）でも
     * 確かめているが、フォームを通らない経路（テストデータ、将来の API など）でも、保存の時点の DB のエラー（500）ではなく、作る時点で気づけるようにする。
     * 文字数はフォームの {@code @Size} と同じく {@link String#length()} で数える。本文の値はメッセージに入れない（ログや画面に出ることがあるため）。
     */
    private static String requireValidContent(String content) {
        if (Objects.requireNonNull(content, "content").isBlank()) {
            throw new IllegalArgumentException("Post content must not be blank");
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("Post content must be at most " + CONTENT_MAX_LENGTH + " characters");
        }
        if (INVISIBLE_CHAR.matcher(content).find()) {
            throw new IllegalArgumentException("Post content must not contain control or invisible characters");
        }
        return requireSingleLine(content);
    }

    /**
     * 本文に改行を入れさせない。日記のプロンプトでは 1 行に 1 つぶやきを並べるので、改行で偽の行を紛れ込ませられないようにする。
     *
     * <p>フォーム（{@code PostForm}・{@code PostEditForm}）でも拒否しているが、フォームを通らない経路や、将来チェックが外れたときに備えてここでも守る。
     * 本文の値はメッセージに入れない（ログや画面に出ることがあるため）。
     */
    private static String requireSingleLine(String content) {
        if (content.indexOf('\n') >= 0 || content.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Post content must not contain line breaks");
        }
        return content;
    }
}
