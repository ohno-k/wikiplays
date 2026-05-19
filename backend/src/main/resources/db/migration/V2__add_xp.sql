-- ============================================================================
--  V2: XP / レベル機構の追加
--    - xp                 : 累計獲得 XP (レベルは XP から導出するため DB には持たない)
--    - xp_earned_today    : 当日中に獲得した XP (デイリーキャップ判定用)
--    - xp_day             : 上記カウンタが指す日付 (日付が変われば 0 リセット)
-- ============================================================================

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS xp BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS xp_earned_today INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS xp_day DATE;
