package com.wikiplays.config;

import com.wikiplays.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Stripe Webhook は認証不要 (Stripe 署名で検証)
                .requestMatchers("/api/stripe/webhook").permitAll()
                // 認証関連 (登録・ログイン) は誰でも
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                // 既存の遊びエンドポイントは誰でも (匿名プレイ可)
                .requestMatchers("/api/article/**", "/api/daily/**").permitAll()
                .requestMatchers("/api/leaderboard/**", "/api/play/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                // コミュニティジャンルは GET/DELETE/random は誰でも、POST (作成) は controller 内で Premium チェック
                .requestMatchers("/api/community-genres/**").permitAll()
                // /api/auth/me と /api/subscription/** などは認証必須
                .requestMatchers("/api/auth/me", "/api/subscription/me", "/api/subscription/checkout", "/api/subscription/portal").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(h -> h.frameOptions(fo -> fo.disable())); // H2 console 用
        return http.build();
    }
}
