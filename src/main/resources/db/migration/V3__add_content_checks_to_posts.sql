-- つぶやきの本文の決まりの一部を、DB でも守る（docs/phase2-spec.md の「5. 画面 > 入力チェック（変更）」）。
-- フォーム（PostForm・PostEditForm）と Post でも拒否しているが、DB に直接書く経路（db/dev-seed.sql、Adminer、手で流す SQL）は
-- それらを通らないため。
--
-- ■ 守るもの
--   posts_content_not_blank：空・空白だけでない。空白の文字は Java の Character#isWhitespace と同じ（Post の isBlank に合わせる）
--   posts_content_no_invisible_chars：見えない文字を含まない。文字は Post.INVISIBLE_CHAR_CLASS と同じ（改行以外の制御文字、書式文字、
--     行・段落の区切り。絵文字をつなぐ U+200D は除く）
--   PostgreSQL の正規表現は Unicode の文字の種類（\p{Cf} など）を使えないので、Java 21 で当てはまる文字の範囲を書き出した。
--   PostContentCheckMigrationTest が、Java の定義と食い違っていないかを確かめる（Java の Unicode の版が上がって増えたら、そこで気づく）
--
-- ■ 守らないもの
--   改行（CR・LF）：フェーズ 1 で保存された改行入りのつぶやきは残す決まりで、その行を UPDATE（カテゴリの変更など）すると
--     制約違反になるため。改行はフォーム・Post・日記のプロンプト（空白に置き換える）で守る
--   文字数：列の型 VARCHAR(100) で守る（DB は文字（コードポイント）で数え、フォームと Post は UTF-16 の単位で数えるので、DB のほうがゆるい）
--
-- ■ NOT VALID
--   足す時点の既存の行は確かめない（決まりを破る行があっても、マイグレーションを止めない）。新しく入れる行と、UPDATE する行だけを確かめる。
--   既存の行が決まりを破っていると、その行の UPDATE は失敗する。直したあとで VALIDATE CONSTRAINT を流せば、既存の行も確かめた制約になる
ALTER TABLE posts
    ADD CONSTRAINT posts_content_not_blank
        CHECK (content ~ '[^\u0009-\u000D\u001C-\u0020\u1680\u2000-\u2006\u2008-\u200A\u2028-\u2029\u205F\u3000]')
        NOT VALID,
    ADD CONSTRAINT posts_content_no_invisible_chars
        CHECK (content !~ '[\u0001-\u0009\u000B-\u000C\u000E-\u001F\u007F-\u009F\u00AD\u0600-\u0605\u061C\u06DD\u070F\u0890-\u0891\u08E2\u180E\u200B-\u200C\u200E-\u200F\u2028-\u202E\u2060-\u2064\u2066-\u206F\uFEFF\uFFF9-\uFFFB\U000110BD\U000110CD\U00013430-\U0001343F\U0001BCA0-\U0001BCA3\U0001D173-\U0001D17A\U000E0001\U000E0020-\U000E007F]')
        NOT VALID;
