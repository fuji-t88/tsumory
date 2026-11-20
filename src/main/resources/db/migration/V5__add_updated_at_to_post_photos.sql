-- 写真を差し替えた日時（docs/phase3-spec.md の「3. データベース」）。差し替え（PH-REQ-05）のために足す。
-- 既存の行は、保存した日時（created_at）を入れてから NOT NULL にする。
ALTER TABLE post_photos ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE post_photos SET updated_at = created_at;
ALTER TABLE post_photos ALTER COLUMN updated_at SET NOT NULL;
