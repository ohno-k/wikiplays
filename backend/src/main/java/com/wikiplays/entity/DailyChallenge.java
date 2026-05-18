package com.wikiplays.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 日別 (scope, genre) ごとに 5 問の問題を固定保存するエンティティ。
 * 同じ日の同じ scope/genre なら、誰がアクセスしても同じ問題を返す。
 */
@Entity
@Table(
    name = "daily_challenge",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_daily_challenge",
        columnNames = {"date", "scope_key", "genre_key"}
    ),
    indexes = @Index(name = "idx_daily_challenge_date", columnList = "date")
)
public class DailyChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    /** "" は scope なし (総合)。null を避けて UniqueConstraint を安定させる。 */
    @Column(name = "scope_key", nullable = false, length = 16)
    private String scopeKey;

    @Column(name = "genre_key", nullable = false, length = 32)
    private String genreKey;

    /** 5 問の記事タイトルをカンマ区切りで保存。 */
    @Column(nullable = false, length = 2000)
    private String articleTitlesCsv;

    @Column(nullable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getGenreKey() { return genreKey; }
    public void setGenreKey(String genreKey) { this.genreKey = genreKey; }
    public String getArticleTitlesCsv() { return articleTitlesCsv; }
    public void setArticleTitlesCsv(String articleTitlesCsv) { this.articleTitlesCsv = articleTitlesCsv; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
