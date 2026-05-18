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
 * 1v1 の非同期チャレンジ。
 * Creator がプレイ完了後、Recipient にチャレンジ送信 → Recipient が同じ問題でプレイ → スコア比較。
 *
 * 問題セットは記事タイトルのタブ区切り CSV で保存 (DailyChallenge と同じ形式)。
 */
@Entity
@Table(
    name = "challenge",
    indexes = {
        @Index(name = "idx_challenge_creator", columnList = "creator_id"),
        @Index(name = "idx_challenge_recipient", columnList = "recipient_id"),
        @Index(name = "idx_challenge_status", columnList = "status")
    }
)
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(nullable = false, length = 16)
    private String mode = "a";

    @Column(length = 32)
    private String genre;

    @Column(length = 16)
    private String scope;

    @Column(name = "community_genre_id")
    private Long communityGenreId;

    @Column(nullable = false, length = 2000)
    private String articleTitlesCsv;

    @Column(name = "creator_score", nullable = false)
    private int creatorScore;

    @Column(name = "recipient_score")
    private Integer recipientScore;

    /** PENDING (受信者未プレイ) / COMPLETED (両者プレイ済み) */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getCommunityGenreId() { return communityGenreId; }
    public void setCommunityGenreId(Long communityGenreId) { this.communityGenreId = communityGenreId; }
    public String getArticleTitlesCsv() { return articleTitlesCsv; }
    public void setArticleTitlesCsv(String articleTitlesCsv) { this.articleTitlesCsv = articleTitlesCsv; }
    public int getCreatorScore() { return creatorScore; }
    public void setCreatorScore(int creatorScore) { this.creatorScore = creatorScore; }
    public Integer getRecipientScore() { return recipientScore; }
    public void setRecipientScore(Integer recipientScore) { this.recipientScore = recipientScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
