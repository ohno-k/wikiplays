package com.wikiplays.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * サーバー側で進行を管理するゲームセッション (A モード / デイリー)。
 * 答え・段落・採点は全てサーバーが持ち、クライアントには開示済みの段落と 4 択だけを渡す。
 */
@Entity
@Table(
    name = "game_session",
    indexes = {
        @Index(name = "idx_game_session_user", columnList = "user_id"),
        @Index(name = "idx_game_session_player", columnList = "player_id"),
        @Index(name = "idx_game_session_created", columnList = "created_at")
    }
)
public class GameSession {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "player_id", length = 64)
    private String playerId;

    /** "a" または "daily"。 */
    @Column(nullable = false, length = 16)
    private String mode;

    @Column(length = 32)
    private String genre;

    @Column(length = 16)
    private String scope;

    @Column(name = "community_genre_id")
    private Long communityGenreId;

    @Column(name = "daily_challenge_id")
    private Long dailyChallengeId;

    @Column(length = 16)
    private String difficulty;

    @Column(name = "reveal_interval_ms", nullable = false)
    private int revealIntervalMs;

    /** プレイヤーが選んだ記事の知名度 tier (1 = 常識レベル 〜 5 = 超マニアック)。null は指定なし。 */
    @Column(name = "fame_tier")
    private Integer fameTier;

    /** 問題・進行状態の JSON (SessionState)。 */
    @Column(name = "state_json", nullable = false, columnDefinition = "TEXT")
    private String stateJson;

    /** セッション開始時に作った play_record の id (終了時にスコアを書き戻す)。 */
    @Column(name = "play_record_id")
    private Long playRecordId;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Column(nullable = false)
    private boolean finished;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getCommunityGenreId() { return communityGenreId; }
    public void setCommunityGenreId(Long communityGenreId) { this.communityGenreId = communityGenreId; }
    public Long getDailyChallengeId() { return dailyChallengeId; }
    public void setDailyChallengeId(Long dailyChallengeId) { this.dailyChallengeId = dailyChallengeId; }
    public Integer getFameTier() { return fameTier; }
    public void setFameTier(Integer fameTier) { this.fameTier = fameTier; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public int getRevealIntervalMs() { return revealIntervalMs; }
    public void setRevealIntervalMs(int revealIntervalMs) { this.revealIntervalMs = revealIntervalMs; }
    public String getStateJson() { return stateJson; }
    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
    public Long getPlayRecordId() { return playRecordId; }
    public void setPlayRecordId(Long playRecordId) { this.playRecordId = playRecordId; }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
