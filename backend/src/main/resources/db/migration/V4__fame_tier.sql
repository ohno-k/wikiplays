-- ============================================================================
--  V4: 記事の知名度スコア / プレイヤーが選ぶ知名度 tier
--    - cached_article.fame_score : FameScorer で計算した主題の知名度 (大きいほど有名)。
--                                  null は未計算 (起動時バックフィルで埋まる)
--    - game_session.fame_tier    : セッション開始時にプレイヤーが選んだ知名度 tier (1〜5、null は指定なし)
-- ============================================================================

ALTER TABLE cached_article ADD COLUMN IF NOT EXISTS fame_score DOUBLE PRECISION;
CREATE INDEX IF NOT EXISTS idx_cached_article_fame ON cached_article(scope, genre, fame_score);

ALTER TABLE game_session ADD COLUMN IF NOT EXISTS fame_tier INTEGER;
