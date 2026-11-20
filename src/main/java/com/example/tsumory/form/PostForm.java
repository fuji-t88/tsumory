package com.example.tsumory.form;

import com.example.tsumory.domain.Post;
import com.example.tsumory.domain.PostPhoto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿フォーム。本文に改行と見えない文字（{@link Post#INVISIBLE_CHAR_CLASS}）を入れさせない（{@code Post} でも同じ値を拒否する。理由は
 * phase2-spec.md の「5. 画面 > 入力チェック（変更。投稿・編集共通）」）。写真は任意で、{@link PostPhoto#MAX_PER_POST} 枚まで
 * （phase3-spec.md の「5. 画面 > 入力チェック（追加）」）。
 *
 * @param photo 送られた写真の部分。写真を選ばなかったときも、ブラウザは中身が空の部分を 1 つ送る。写真の欄がないリクエスト（{@code null}）は空のリストにする
 */
public record PostForm(
        @NotBlank(message = "内容を入力してください")
        @Size(max = Post.CONTENT_MAX_LENGTH, message = "{max} 文字以内で入力してください")
        @Pattern(regexp = "[^\r\n]*", message = "改行は入れられません")
        @Pattern(regexp = Post.NO_INVISIBLE_CHARS_REGEX, message = "見えない文字や制御文字は入れられません")
        String content,

        List<MultipartFile> photo) {

    public PostForm {
        photo = photo == null ? List.of() : List.copyOf(photo);
    }

    /** 空の投稿フォーム（画面を開いたとき）。 */
    public static PostForm empty() {
        return new PostForm(null, List.of());
    }

    /**
     * 添付された写真。写真を選ばなかったときの部分（ファイル名も中身も空）は除く。ファイル名があれば、中身が空でも残す（空のファイルを選んだとき。
     * service が受け付けない写真として断る）。
     */
    @Size(max = PostPhoto.MAX_PER_POST, message = "写真は {max} 枚まで添付できます")
    public List<MultipartFile> getAttachedPhotos() {
        return photo.stream().filter(PostForm::isChosen).toList();
    }

    private static boolean isChosen(MultipartFile file) {
        var name = file.getOriginalFilename();
        return !file.isEmpty() || (name != null && !name.isEmpty());
    }
}
