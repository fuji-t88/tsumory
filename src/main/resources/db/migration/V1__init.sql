-- V1__init.sql
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(72) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE posts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content    VARCHAR(100) NOT NULL,
    posted_at  TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_posts_user_id_posted_at ON posts (user_id, posted_at);

CREATE TABLE diaries (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    diary_date   DATE NOT NULL,
    content      TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, diary_date)
);

-- 初期ユーザー demo。password_hash は BCrypt のハッシュ値（平文はここに書かない）
INSERT INTO users (username, password_hash)
VALUES ('demo', '$2a$10$u5wRnXwe0MZ/QyZ2FTFdle646EshVuusAXWewRF8KfjDHrp6BtyhO');
