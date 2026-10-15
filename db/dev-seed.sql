-- 動作確認用のテストデータ（開発環境だけで使う。本番には流さない）
--
-- ■ ねらい
-- 初期ユーザー demo に、「今日の 7 日前〜昨日」の 7 日分のつぶやきと日記を入れる。
-- 空のデータベースでは日記一覧・カレンダー・検索の画面を確かめられないので、1 週間分を一括で入れる。
--
--   7 日前：つぶやき・日記あり
--   6 日前：つぶやき・日記あり（翌朝に作り直した日記。「M月d日 HH:mm に作成」の表示を確かめる）
--   5 日前：つぶやきあり・日記なし（遡り生成の確認用）
--   4 日前：つぶやき・日記あり（検索の確認用に、% と ! を含むつぶやきがある）
--   3 日前：つぶやきあり・日記なし（遡り生成の確認用）
--   2 日前：つぶやき・日記あり（手で直したカテゴリ、編集済みのつぶやきがある）
--   昨日　：つぶやき・日記あり（昨日のつぶやきは編集・削除もできる）
--   今日　：入れない（自分で投稿して確かめる）
--
-- ■ 日付の決め方
-- 日付は実行した日（日本時間）からの相対で決める（now() から何日前か）。いつ流しても「過去 7 日分」になり、
-- 日記を開ける範囲（登録日から今日まで）や、つぶやきを編集できる範囲（今日と昨日）の前提もずれない。
-- そのかわり曜日は流す日によって変わるので、本文には曜日や「週末」のような、曜日に依存する言葉を書かない。
--
-- ■ 注意（既存データの削除）
-- 流すたびに、demo の「7 日前〜昨日」のつぶやきと日記を**すべて消してから**入れ直す。
-- この期間に自分で投稿したつぶやきや、自分で生成した日記も消える。今日の分と、8 日以上前の分は消さない。
-- 何度流しても同じ状態になる（重複しない）。
-- あわせて、7 日前の日記を開けるよう、demo の登録日時が 30 日前より後なら 30 日前にずらす（DiaryService#isAvailableDate）。
--
-- ■ 置き場所
-- Flyway のマイグレーションではない。src/main/resources/db/migration/ に置くと本番でも自動で流れてしまうので、
-- プロジェクト直下の db/ に置く。
--
-- ■ 流し方（プロジェクト直下で。アプリを起動していなくても、db コンテナが動いていればよい）
--   docker compose exec -T db psql -U tsumory -d tsumory -v ON_ERROR_STOP=1 < db/dev-seed.sql
-- Adminer（http://localhost:8081 、サーバー欄は db）で開く場合は、「SQL コマンド」にこのファイルの中身を貼り付けて実行する。

BEGIN;

-- 登録日より前の日は日記を開けないので、demo の登録日時が 30 日前より後なら、30 日前にずらす（前にずらすだけで、後ろにはずらさない）
UPDATE users
SET created_at = now() - interval '30 days'
WHERE username = 'demo'
  AND created_at > now() - interval '30 days';

-- 「7 日前〜昨日」の demo のつぶやきと日記を消す（入れ直すため）
DELETE FROM posts
WHERE user_id = (SELECT id FROM users WHERE username = 'demo')
  AND posted_at >= ((now() AT TIME ZONE 'Asia/Tokyo')::date - 7)::timestamp AT TIME ZONE 'Asia/Tokyo'
  AND posted_at < ((now() AT TIME ZONE 'Asia/Tokyo')::date)::timestamp AT TIME ZONE 'Asia/Tokyo';

DELETE FROM diaries
WHERE user_id = (SELECT id FROM users WHERE username = 'demo')
  AND diary_date >= (now() AT TIME ZONE 'Asia/Tokyo')::date - 7
  AND diary_date < (now() AT TIME ZONE 'Asia/Tokyo')::date;

-- つぶやき。1 日あたり 4〜5 件（1 日の上限 16 件より少なく）。本文は 100 文字以内で改行なし（アプリの入力チェックと同じ）
-- category はアプリの PostCategory の値（WORK・PRIVATE・LEARNING・OTHER）。本文の話題に合わせる
-- edited_after は、投稿してから編集するまでの時間（0 なら編集していない）
WITH demo AS (
    SELECT id FROM users WHERE username = 'demo'
),
today AS (
    SELECT (now() AT TIME ZONE 'Asia/Tokyo')::date AS d
),
seed (days_ago, posted_time, content, category, set_by_user, edited_after) AS (
    VALUES
        -- 7 日前：新しい案件の担当になった日
        (7, time '07:40', '朝はやっぱり眠い。コーヒーを二杯飲んで出社', 'PRIVATE', false, interval '0'),
        (7, time '10:15', '朝会で、新しい案件の担当になった', 'WORK', false, interval '0'),
        (7, time '12:30', '昼は会社の近くのそば屋で天ぷらそば', 'PRIVATE', false, interval '0'),
        (7, time '19:50', '帰りの電車でTOEICの単語帳を20ページ進めた', 'LEARNING', false, interval '0'),
        (7, time '23:10', '新しい案件、ちょっと楽しみ。今日は早めに寝る', 'PRIVATE', false, interval '0'),
        -- 6 日前：雨の日
        (6, time '08:05', '朝から雨。傘を忘れてコンビニで買った', 'PRIVATE', false, interval '0'),
        (6, time '14:20', '新案件のキックオフ。取引先の佐藤さんがとても話しやすい人だった', 'WORK', false, interval '0'),
        (6, time '18:45', '残業せずに帰れた。夕飯は家で鍋', 'PRIVATE', false, interval '0'),
        (6, time '22:30', '寝る前にPythonの入門書を一章読んだ', 'LEARNING', false, interval '0'),
        -- 5 日前：日記なし（遡り生成の確認用）
        (5, time '07:55', '寝坊した。朝ごはん抜きで駅までダッシュ', 'PRIVATE', false, interval '0'),
        (5, time '11:00', '午前中は見積書の作成でずっとExcelとにらめっこ', 'WORK', false, interval '0'),
        (5, time '12:40', '昼のカレーが辛すぎた。でもおいしい', 'PRIVATE', false, interval '0'),
        (5, time '16:30', '午後の定例会議が1時間も延びた', 'WORK', false, interval '0'),
        (5, time '21:15', '帰りにパン屋で塩バターパンを買った。明日の朝ごはん', 'PRIVATE', false, interval '0'),
        -- 4 日前：検索の確認用（% と ! を含む）
        (4, time '08:20', '昨日の塩バターパン、温めたら最高だった', 'PRIVATE', false, interval '0'),
        (4, time '13:10', '英語の小テスト、正答率80%だった!前回より上がった', 'LEARNING', false, interval '0'),
        (4, time '15:45', '見積書を提出。佐藤さんから「助かります」と返事が来た', 'WORK', false, interval '0'),
        (4, time '20:30', 'ジムで30分走った。久しぶりで脚がぱんぱん', 'PRIVATE', false, interval '0'),
        -- 3 日前：日記なし（遡り生成の確認用）。休みの日
        (3, time '09:30', '休みの日。目覚ましをかけずに起きたら九時半だった', 'PRIVATE', false, interval '0'),
        (3, time '11:00', '友だちとカフェでブランチ。パンケーキが分厚い', 'PRIVATE', false, interval '0'),
        (3, time '15:00', '図書館でTOEICの模試を一回分解いた', 'LEARNING', false, interval '0'),
        (3, time '19:30', '夜は実家に電話。母が元気そうでよかった', 'PRIVATE', false, interval '0'),
        -- 2 日前：手で直したカテゴリ、編集済みのつぶやき
        (2, time '10:00', '部屋の掃除と洗濯をまとめて片づけた', 'PRIVATE', false, interval '0'),
        (2, time '13:30', '取引先の佐藤さんとランチしながら次の打ち合わせ', 'WORK', true, interval '0'),
        (2, time '16:00', 'Pythonの練習問題を三問解いた。リスト内包表記に慣れてきた', 'LEARNING', false, interval '25 minutes'),
        (2, time '22:00', '明日も早いので、そろそろ寝る', 'PRIVATE', false, interval '0'),
        -- 昨日
        (1, time '07:45', '今日は珍しく目覚ましより先に起きた', 'PRIVATE', false, interval '0'),
        (1, time '11:30', '新案件の資料を佐藤さんに送った。次はレビュー', 'WORK', false, interval '0'),
        (1, time '12:20', '昼は駅前の定食屋で唐揚げ定食', 'PRIVATE', false, interval '0'),
        (1, time '20:10', '通勤電車で英単語アプリを30分続けた。連続10日目', 'LEARNING', false, interval '0')
)
INSERT INTO posts (user_id, content, posted_at, updated_at, category, category_set_by_user)
SELECT demo.id,
       s.content,
       ((today.d - s.days_ago) + s.posted_time) AT TIME ZONE 'Asia/Tokyo',
       ((today.d - s.days_ago) + s.posted_time + s.edited_after) AT TIME ZONE 'Asia/Tokyo',
       s.category,
       s.set_by_user
FROM seed s
CROSS JOIN demo
CROSS JOIN today;

-- 日記。その日のつぶやきから書いたような内容にする（AI が書いた体裁）。5 日前と 3 日前は入れない（遡り生成の確認用）
-- generated_days_after は、その日から何日後に書いたか（0 ならその日の夜。1 なら翌日に書き直した）
WITH demo AS (
    SELECT id FROM users WHERE username = 'demo'
),
today AS (
    SELECT (now() AT TIME ZONE 'Asia/Tokyo')::date AS d
),
seed (days_ago, generated_days_after, generated_time, content) AS (
    VALUES
        (7, 0, time '23:20',
         E'朝はやっぱり眠くて、コーヒーを二杯飲んでから出社した。朝会で新しい案件の担当になり、少し身が引き締まる。\n'
         || E'昼は会社の近くのそば屋で天ぷらそば。帰りの電車ではTOEICの単語帳を20ページ進めた。\n'
         || '新しい案件はちょっと楽しみだ。今日は早めに寝ることにする。'),
        (6, 1, time '07:50',
         E'朝から雨で、傘を忘れてコンビニで買うはめになった。\n'
         || E'午後は新案件のキックオフ。取引先の佐藤さんがとても話しやすい人で、ほっとした。\n'
         || '残業せずに帰れたので、夕飯は家で鍋。寝る前にPythonの入門書を一章読んだ。雨の日にしては、悪くない一日だった。'),
        (4, 0, time '22:40',
         E'昨日買った塩バターパンを温めて食べたら、最高においしかった。\n'
         || E'英語の小テストは正答率80%で、前回より上がった。見積書も提出できて、佐藤さんから「助かります」と返事が来た。\n'
         || '夜は久しぶりにジムで30分走った。脚がぱんぱんだけど、気分はすっきりしている。'),
        (2, 0, time '22:30',
         E'午前中に部屋の掃除と洗濯をまとめて片づけた。\n'
         || E'昼は取引先の佐藤さんとランチをしながら、次の打ち合わせ。話が弾んで楽しかった。\n'
         || '午後はPythonの練習問題を三問。リスト内包表記にだいぶ慣れてきた。明日も早いので、今日はもう寝る。'),
        (1, 0, time '22:15',
         E'今日は珍しく目覚ましより先に起きられて、朝から少し得した気分。\n'
         || E'新案件の資料を佐藤さんに送り、次はレビューを待つだけになった。昼は駅前の定食屋で唐揚げ定食。\n'
         || '通勤電車の英単語アプリは連続10日目。小さく続けられているのがうれしい。')
)
INSERT INTO diaries (user_id, diary_date, content, generated_at)
SELECT demo.id,
       today.d - s.days_ago,
       s.content,
       ((today.d - s.days_ago + s.generated_days_after) + s.generated_time) AT TIME ZONE 'Asia/Tokyo'
FROM seed s
CROSS JOIN demo
CROSS JOIN today;

COMMIT;
