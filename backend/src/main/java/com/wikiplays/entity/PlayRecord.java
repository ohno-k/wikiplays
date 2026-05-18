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
 * サーバー側のプレイ履歴。
 * ログインユーザーのプレイをここに記録する (LocalStorage との二重保存)。
 * 匿名プレイは player_id を使う (将来アカウント連携時に user_id へマージ可能)。
 */
@Entity
@Table(
    name = "play_record",
    indexes = {
        @Index(name = "idx_play_record_user", columnList = "user_id"),
        @Index(name = "idx_play_record_player", columnList = "player_id"),
        @Index(name = "idx_play_record_played", columnList = "playedAt")
    }
)
public class PlayRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /** ログインしていない匿名プレイの場合の識別子。 */
    @Column(name = "player_id", length = 64)
    private String playerId;

    /** "a" (A モード) または "daily" (デイリー) など。 */
    @Column(nullable = false, length = 16)
    private String mode;

    @Column(length = 32)
    private String genre;

    @Column(length = 16)
    private String scope;

    @Column(name = "community_genre_id")
    private Long communityGenreId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int maxScore;

    @Column(length = 16)
    private String difficulty;

    @Column(nullable = false)
    private Instant playedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getMaxScore() { return maxScore; }
    public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public Instant getPlayedAt() { return playedAt; }
    public void setPlayedAt(Instant playedAt) { this.playedAt = playedAt; }
}
