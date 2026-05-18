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

/**
 * 認証済みユーザー。
 * 認証なしで遊べる匿名プレイヤー (playerId) と並行して、本サービスに登録した
 * ユーザーをこのテーブルで管理する。
 */
@Entity
@Table(
    name = "app_user",  // "user" は予約語の DB が多いので回避
    uniqueConstraints = @UniqueConstraint(name = "uq_app_user_email", columnNames = "email"),
    indexes = @Index(name = "idx_app_user_email", columnList = "email")
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    /** BCrypt ハッシュ済みパスワード。 */
    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String displayName;

    /** 役割。"USER" / "ADMIN" */
    @Column(nullable = false, length = 16)
    private String role = "USER";

    @Column(nullable = false)
    private boolean emailVerified = false;

    /** 匿名プレイヤー時代の playerId をここに紐づける (移行用)。 */
    @Column(length = 64)
    private String legacyPlayerId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant lastLoginAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getLegacyPlayerId() { return legacyPlayerId; }
    public void setLegacyPlayerId(String legacyPlayerId) { this.legacyPlayerId = legacyPlayerId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
