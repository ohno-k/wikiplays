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
 * フィルタ通過済みの Wikipedia 記事をキャッシュするテーブル。
 * 詳細データは JSON 列に格納する (PostgreSQL/H2 両対応の TEXT)。
 */
@Entity
@Table(
    name = "cached_article",
    indexes = {
        @Index(name = "idx_cached_article_scope_genre", columnList = "scope,genre"),
        @Index(name = "idx_cached_article_title", columnList = "title", unique = true)
    }
)
public class CachedArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    /** scope (jp/world)。記事プールではキャッシュ時のスコープを記録。null は scope 不問。 */
    @Column(length = 16)
    private String scope;

    /** ジャンル ID。null は総合 (ランダム取得)。 */
    @Column(length = 32)
    private String genre;

    /** ArticleData 全体を JSON 文字列でシリアライズして保存。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String dataJson;

    /** 抽出された主題の年。null も OK。 */
    @Column
    private Integer extractedYear;

    @Column(length = 16)
    private String extractedYearKind;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant lastUsedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    public Integer getExtractedYear() { return extractedYear; }
    public void setExtractedYear(Integer extractedYear) { this.extractedYear = extractedYear; }
    public String getExtractedYearKind() { return extractedYearKind; }
    public void setExtractedYearKind(String extractedYearKind) { this.extractedYearKind = extractedYearKind; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
