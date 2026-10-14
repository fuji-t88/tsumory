-- V2__add_category_to_posts.sql
-- posts に AI 自動分類のカテゴリ列を追加する（docs/phase2-spec.md の「3. データベース」）。
-- 値は PostCategory enum の name()（WORK / PRIVATE / LEARNING / OTHER）。
-- NOT NULL DEFAULT 'OTHER':
--   既存行にも値が入るので、アプリ層で NULL を扱わずに済む。
--   既定値が効くのは INSERT で列を省いたときだけ。分類に失敗したときに OTHER にするのは
--   アプリ層（PostClassifier が OTHER を返し、それを UPDATE する）の役目で、DB は面倒を見ない。
-- CHECK 制約は張らない:
--   値の妥当性はアプリ層の enum で担保する。DB 制約にすると毎回マイグレーションが要る。
ALTER TABLE posts
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'OTHER',
    -- ユーザーが手でカテゴリを直したら true。true のつぶやきは AI の判定で上書きしない
    ADD COLUMN category_set_by_user BOOLEAN NOT NULL DEFAULT false;

-- 検索のカテゴリ絞り込み用
CREATE INDEX idx_posts_user_id_category_posted_at ON posts (user_id, category, posted_at);
