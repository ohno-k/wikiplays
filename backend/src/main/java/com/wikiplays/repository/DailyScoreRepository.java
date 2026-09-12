package com.wikiplays.repository;

import com.wikiplays.entity.DailyScore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DailyScoreRepository extends JpaRepository<DailyScore, Long> {

    /** ランキング: スコア降順、同点は早く達成した順。 */
    List<DailyScore> findByDailyChallengeIdOrderByScoreDescPlayedAtAsc(Long dailyChallengeId, Pageable pageable);

    /** 同じプレイヤーが既に挑戦済みかチェック。 */
    Optional<DailyScore> findFirstByDailyChallengeIdAndPlayerId(Long dailyChallengeId, String playerId);

    /** チャレンジ全体の挑戦者数。 */
    long countByDailyChallengeId(Long dailyChallengeId);

    /** あるプレイヤーの全スコア (表示名の付け替え用)。 */
    List<DailyScore> findByPlayerId(String playerId);
}
