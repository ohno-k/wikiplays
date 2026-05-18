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
 * ユーザーが作成した独自ジャンル (コミュニティジャンル)。
 * 認証不要、作成者は匿名 ID (ブラウザ) で記録。
 */
@Entity
@Table(
    name = "custom_genre",
    indexes = {
        @Index(name = "idx_custom_genre_creator", columnList = "creator_id"),
        @Index(name = "idx_custom_genre_created", columnList = "createdAt")
    }
)
public class CustomGenre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 10)
    private String emoji;

    /** Wikipedia カテゴリをタブ区切りで保存。 */
    @Column(name = "categories_csv", nullable = false, length = 2000)
    private String categoriesCsv;

    /** 作成者のブラウザ ID (匿名)。 */
    @Column(name = "creator_id", nullable = false, length = 64)
    private String creatorId;

    @Column(length = 32)
    private String creatorName;

    @Column(nullable = false)
    private Instant createdAt;

    /** プレイ回数。 */
    @Column(nullable = false)
    private long playCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getCategoriesCsv() { return categoriesCsv; }
    public void setCategoriesCsv(String categoriesCsv) { this.categoriesCsv = categoriesCsv; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public long getPlayCount() { return playCount; }
    public void setPlayCount(long playCount) { this.playCount = playCount; }
}
