-- つぶやきに添付した写真（docs/phase3-spec.md の「3. データベース」）。
-- 写真はこのテーブルにバイナリで持つ（外部のストレージを使わない理由は「2. 写真の保存先（PostgreSQL）」）。
--
-- ■ 守るもの
--   主キー（post_id）：1 つのつぶやきに写真は 1 枚まで
--   外部キー（ON DELETE CASCADE）：つぶやきを消すと写真も消える
--   post_photos_data_not_empty：写真のデータが空でない
--   post_photos_size_matches：size_bytes が写真のデータの長さと同じ
--
-- ■ 守らないもの
--   容量の上限・形式（JPEG・PNG）：仕様が変わりうる業務ルールなので、service とフォームで守る
CREATE TABLE post_photos (
    post_id      BIGINT       PRIMARY KEY REFERENCES posts (id) ON DELETE CASCADE,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    data         BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT post_photos_data_not_empty CHECK (octet_length(data) > 0),
    CONSTRAINT post_photos_size_matches CHECK (size_bytes = octet_length(data))
);
