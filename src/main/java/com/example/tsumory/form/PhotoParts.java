package com.example.tsumory.form;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** フォームで受け取った写真の部分の扱い。投稿フォームと編集フォームで同じ判定を使う（phase3-spec.md の「5. 画面 > 入力チェック（追加）」）。 */
final class PhotoParts {

    private PhotoParts() {}

    /**
     * 選んだ写真の部分だけを返す。写真を選ばなかったときにブラウザが送る部分（ファイル名も中身も空）は除く。ファイル名があれば、中身が空でも残す
     * （空のファイルを選んだとき。service が受け付けない写真として断る）。
     */
    static List<MultipartFile> chosen(List<MultipartFile> parts) {
        return parts.stream().filter(PhotoParts::isChosen).toList();
    }

    private static boolean isChosen(MultipartFile file) {
        var name = file.getOriginalFilename();
        return !file.isEmpty() || (name != null && !name.isEmpty());
    }
}
