-- ============================================================================
--  V1 baseline schema
--  本番 DB は baseline-on-migrate=true で flyway_schema_history のみ作成され、
--  この SQL は実行されない (既に Hibernate ddl-auto で作成済みのため)。
--  新規環境 (fresh DB) では本 SQL が実行されてスキーマが作成される。
-- ============================================================================

-- ユーザー
CREATE TABLE app_user (
  id              BIGSERIAL PRIMARY KEY,
  email           VARCHAR(255) NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,
  display_name    VARCHAR(32) NOT NULL,
  role            VARCHAR(16) NOT NULL DEFAULT 'USER',
  email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
  legacy_player_id VARCHAR(64),
  created_at      TIMESTAMP NOT NULL,
  last_login_at   TIMESTAMP
);
CREATE UNIQUE INDEX uq_app_user_email ON app_user(email);
CREATE INDEX idx_app_user_email ON app_user(email);

-- サブスクリプション
CREATE TABLE subscription (
  id                       BIGSERIAL PRIMARY KEY,
  user_id                  BIGINT NOT NULL,
  plan                     VARCHAR(16) NOT NULL DEFAULT 'FREE',
  status                   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  trial_end                TIMESTAMP,
  stripe_customer_id       VARCHAR(64),
  stripe_subscription_id   VARCHAR(64),
  current_period_end       TIMESTAMP,
  cancelled_at             TIMESTAMP,
  created_at               TIMESTAMP NOT NULL,
  updated_at               TIMESTAMP
);
CREATE UNIQUE INDEX uq_subscription_user ON subscription(user_id);
CREATE INDEX idx_subscription_stripe_sub ON subscription(stripe_subscription_id);
CREATE INDEX idx_subscription_stripe_customer ON subscription(stripe_customer_id);

-- デイリーチャレンジ (日別の問題セット)
CREATE TABLE daily_challenge (
  id                  BIGSERIAL PRIMARY KEY,
  date                DATE NOT NULL,
  scope_key           VARCHAR(16) NOT NULL,
  genre_key           VARCHAR(32) NOT NULL,
  article_titles_csv  VARCHAR(2000) NOT NULL,
  created_at          TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX uq_daily_challenge ON daily_challenge(date, scope_key, genre_key);
CREATE INDEX idx_daily_challenge_date ON daily_challenge(date);

-- デイリースコア
CREATE TABLE daily_score (
  id                   BIGSERIAL PRIMARY KEY,
  daily_challenge_id   BIGINT NOT NULL,
  player_id            VARCHAR(64) NOT NULL,
  display_name         VARCHAR(32),
  score                INTEGER NOT NULL,
  played_at            TIMESTAMP NOT NULL
);
CREATE INDEX idx_daily_score_challenge ON daily_score(daily_challenge_id);
CREATE INDEX idx_daily_score_player ON daily_score(player_id);

-- プレイ履歴 (全体ランキング集計用)
CREATE TABLE play_record (
  id                  BIGSERIAL PRIMARY KEY,
  user_id             BIGINT,
  player_id           VARCHAR(64),
  mode                VARCHAR(16) NOT NULL,
  genre               VARCHAR(32),
  scope               VARCHAR(16),
  community_genre_id  BIGINT,
  score               INTEGER NOT NULL,
  max_score           INTEGER NOT NULL,
  difficulty          VARCHAR(16),
  played_at           TIMESTAMP NOT NULL
);
CREATE INDEX idx_play_record_user ON play_record(user_id);
CREATE INDEX idx_play_record_player ON play_record(player_id);
CREATE INDEX idx_play_record_played ON play_record(played_at);

-- 記事キャッシュ
CREATE TABLE cached_article (
  id                    BIGSERIAL PRIMARY KEY,
  title                 VARCHAR(500) NOT NULL,
  scope                 VARCHAR(16),
  genre                 VARCHAR(32),
  community_genre_id    BIGINT,
  data_json             TEXT NOT NULL,
  extracted_year        INTEGER,
  extracted_year_kind   VARCHAR(16),
  created_at            TIMESTAMP NOT NULL,
  last_used_at          TIMESTAMP
);
CREATE UNIQUE INDEX idx_cached_article_title ON cached_article(title);
CREATE INDEX idx_cached_article_scope_genre ON cached_article(scope, genre);
CREATE INDEX idx_cached_article_community ON cached_article(community_genre_id);

-- コミュニティジャンル (ユーザー作成 + 公式シード)
CREATE TABLE custom_genre (
  id              BIGSERIAL PRIMARY KEY,
  name            VARCHAR(50) NOT NULL,
  emoji           VARCHAR(10),
  categories_csv  VARCHAR(2000) NOT NULL,
  creator_id      VARCHAR(64) NOT NULL,
  creator_name    VARCHAR(32),
  created_at      TIMESTAMP NOT NULL,
  play_count      BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_custom_genre_creator ON custom_genre(creator_id);
CREATE INDEX idx_custom_genre_created ON custom_genre(created_at);

-- メール認証 / パスワードリセットトークン
CREATE TABLE email_token (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  type        VARCHAR(32) NOT NULL,
  token       VARCHAR(128) NOT NULL,
  expires_at  TIMESTAMP NOT NULL,
  used        BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX idx_email_token_token ON email_token(token);
CREATE INDEX idx_email_token_user ON email_token(user_id);

-- フレンド関係
CREATE TABLE friendship (
  id            BIGSERIAL PRIMARY KEY,
  requester_id  BIGINT NOT NULL,
  addressee_id  BIGINT NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at    TIMESTAMP NOT NULL,
  responded_at  TIMESTAMP
);
CREATE UNIQUE INDEX uq_friendship_pair ON friendship(requester_id, addressee_id);
CREATE INDEX idx_friendship_requester ON friendship(requester_id);
CREATE INDEX idx_friendship_addressee ON friendship(addressee_id);

-- 非同期チャレンジ (1v1)
CREATE TABLE challenge (
  id                  BIGSERIAL PRIMARY KEY,
  creator_id          BIGINT NOT NULL,
  recipient_id        BIGINT NOT NULL,
  mode                VARCHAR(16) NOT NULL DEFAULT 'a',
  genre               VARCHAR(32),
  scope               VARCHAR(16),
  community_genre_id  BIGINT,
  article_titles_csv  VARCHAR(2000) NOT NULL,
  creator_score       INTEGER NOT NULL,
  recipient_score     INTEGER,
  status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at          TIMESTAMP NOT NULL,
  completed_at        TIMESTAMP
);
CREATE INDEX idx_challenge_creator ON challenge(creator_id);
CREATE INDEX idx_challenge_recipient ON challenge(recipient_id);
CREATE INDEX idx_challenge_status ON challenge(status);
