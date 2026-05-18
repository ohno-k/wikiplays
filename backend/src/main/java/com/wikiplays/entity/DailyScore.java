package com.wikiplays.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * ユーザーがデイリーチャレンジに挑戦した結果のスコア。
 * 認証なしで使えるよう、ハンドル名 (任意) + ブラウザ生成の playerId で識別。
 */
@Entity
@Table(
    name = "daily_score",
    indexes = {
        @Index(name = "idx_daily_score_challenge", columnList = "daily_challenge_id"),
        @Index(name = "idx_daily_score_player", columnList = "player_id"),
    }
)
public class DailyScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "daily_challenge_id", nullable = false)
    private Long dailyChallengeId;

    @Column(name = "player_id", nullable = false, length = 64)
    private String playerId;

    @Column(length = 32)
    private String displayName;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private Instant playedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDailyChallengeId() { return dailyChallengeId; }
    public void setDailyChallengeId(Long dailyChallengeId) { this.dailyChallengeId = dailyChallengeId; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public Instant getPlayedAt() { return playedAt; }
    public void setPlayedAt(Instant playedAt) { this.playedAt = playedAt; }
}
