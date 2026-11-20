package com.example.tsumory.form;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostCategory;
import com.example.tsumory.domain.PostPhoto;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 編集モードのフォーム。本文の検証は {@link PostForm} と同じ（改行・見えない文字も入れさせない）。カテゴリは手で直すときに使う（投稿フォームでは受け取らない）。
 * 写真は、差し替える写真（{@code photo}）か、外す（{@code removePhoto}）のどちらかを選べる（phase3-spec.md の「5. 画面 > 今日のつぶやき（編集モード）」）。
 *
 * @param photo 差し替える写真の部分。写真の欄がないリクエスト（{@code null}）は空のリストにする
 * @param removePhoto 「写真を外す」にチェックを入れたら {@code true}。値が送られなければ {@code false} にする
 */
public record PostEditForm(
        @NotBlank(message = "内容を入力してください")
        @Size(max = Post.CONTENT_MAX_LENGTH, message = "{max} 文字以内で入力してください")
        @Pattern(regexp = "[^\r\n]*", message = "改行は入れられません")
        @Pattern(regexp = Post.NO_INVISIBLE_CHARS_REGEX, message = "見えない文字や制御文字は入れられません")
        String content,

        @NotNull(message = "カテゴリを選んでください") PostCategory category,
        List<MultipartFile> photo,
        Boolean removePhoto) {

    public PostEditForm {
        photo = photo == null ? List.of() : List.copyOf(photo);
        // チェックボックスは、チェックしなければ値が送られない
        removePhoto = Boolean.TRUE.equals(removePhoto);
    }

    /** 編集モードを開いたときのフォーム（今の本文とカテゴリ。写真は変えない）。 */
    public static PostEditForm of(String content, PostCategory category) {
        return new PostEditForm(content, category, List.of(), Boolean.FALSE);
    }

    /** 差し替える写真（{@link PhotoParts#chosen}）。 */
    @Size(max = PostPhoto.MAX_PER_POST, message = "写真は {max} 枚まで添付できます")
    public List<MultipartFile> getAttachedPhotos() {
        return PhotoParts.chosen(photo);
    }

    /** 写真を外すのと差し替えるのを、同時には選べない。 */
    @AssertTrue(message = "写真を外すか差し替えるか、どちらかにしてください")
    public boolean isPhotoChoiceConsistent() {
        return !removePhoto.booleanValue() || getAttachedPhotos().isEmpty();
    }
}
