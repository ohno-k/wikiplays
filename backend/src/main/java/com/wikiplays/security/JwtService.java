package com.wikiplays.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/** JWT 発行・検証サービス。 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
        @Value("${wikiplays.jwt.secret:}") String secret,
        @Value("${wikiplays.jwt.expiration-ms:2592000000}") long expirationMs, // 30 日
        org.springframework.core.env.Environment env
    ) {
        boolean local = env.acceptsProfiles(org.springframework.core.env.Profiles.of("local", "test"));
        if (secret == null || secret.isBlank() || secret.contains("please-change")) {
            if (!local) {
                // 本番で秘密鍵未設定のまま起動すると誰でもトークンを偽造できるので、起動を止める
                throw new IllegalStateException(
                    "wikiplays.jwt.secret (環境変数 JWT_SECRET) が未設定です。32 文字以上のランダム文字列を設定してください。");
            }
            secret = "local-dev-only-jwt-secret-not-for-production-use-0123456789";
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("wikiplays.jwt.secret は 32 バイト以上必要です");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(key)
            .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
