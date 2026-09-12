-- ============================================================================
--  V3: サーバー側ゲームセッション / 連続プレイ日数 / ダミーユーザーフラグ
--    - game_session      : A モード・デイリーの進行状態と採点をサーバーで保持する
--    - app_user.streak_days / last_play_date : 連続プレイ日数
--    - app_user.is_dummy : 運営が投入した架空ユーザー (一括削除・除外用)
-- ============================================================================

CREATE TABLE game_session (
  id                  VARCHAR(36) PRIMARY KEY,
  user_id             BIGINT,
  player_id           VARCHAR(64),
  mode                VARCHAR(16) NOT NULL,
  genre               VARCHAR(32),
  scope               VARCHAR(16),
  community_genre_id  BIGINT,
  daily_challenge_id  BIGINT,
  difficulty          VARCHAR(16),
  reveal_interval_ms  INTEGER NOT NULL,
  state_json          TEXT NOT NULL,
  play_record_id      BIGINT,
  total_score         INTEGER NOT NULL DEFAULT 0,
  finished            BOOLEAN NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMP NOT NULL,
  updated_at          TIMESTAMP NOT NULL
);
CREATE INDEX idx_game_session_user ON game_session(user_id);
CREATE INDEX idx_game_session_player ON game_session(player_id);
CREATE INDEX idx_game_session_created ON game_session(created_at);

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS streak_days INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS last_play_date DATE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS is_dummy BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_app_user_dummy ON app_user(is_dummy);
