package com.wikiplays.repository;

import com.wikiplays.entity.PlayRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PlayRecordRepository extends JpaRepository<PlayRecord, Long> {

    List<PlayRecord> findByUserIdOrderByPlayedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    /**
     * 指定ユーザー or 匿名 playerId のプレイ回数を since 以降でカウント。
     * userId が指定されたら userId 一致のみ、なければ playerId 一致のみ。
     */
    @Query(value = """
        SELECT COUNT(*) FROM play_record
        WHERE played_at >= :since
          AND mode = :mode
          AND (
            (:userId IS NOT NULL AND user_id = :userId)
            OR (:userId IS NULL AND :playerId IS NOT NULL AND player_id = :playerId)
          )
        """, nativeQuery = true)
    long countSince(
        @Param("userId") Long userId,
        @Param("playerId") String playerId,
        @Param("mode") String mode,
        @Param("since") Instant since
    );

    /**
     * ログインユーザー (user_id NOT NULL) のランキング集計。
     * 戻り値: [displayName, totalScore, bestScore, playCount] の配列リスト。
     */
    @Query(value = """
        SELECT u.display_name AS name,
               COALESCE(SUM(pr.score), 0) AS total,
               COALESCE(MAX(pr.score), 0) AS best,
               COUNT(*) AS cnt
        FROM play_record pr
        INNER JOIN app_user u ON pr.user_id = u.id
        WHERE pr.played_at >= :since
          AND (:mode IS NULL OR pr.mode = :mode)
          AND (:genre IS NULL OR pr.genre = :genre)
          AND (:scope IS NULL OR pr.scope = :scope)
          AND (:communityGenreId IS NULL OR pr.community_genre_id = :communityGenreId)
        GROUP BY u.id, u.display_name
        ORDER BY total DESC
        LIMIT :lim
        """, nativeQuery = true)
    List<Object[]> aggregateLeaderboard(
        @Param("since") Instant since,
        @Param("mode") String mode,
        @Param("genre") String genre,
        @Param("scope") String scope,
        @Param("communityGenreId") Long communityGenreId,
        @Param("lim") int limit
    );
}
