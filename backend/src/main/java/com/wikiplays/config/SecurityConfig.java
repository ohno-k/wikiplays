package com.wikiplays.config;

import com.wikiplays.security.JwtAuthFilter;
import com.wikiplays.security.OAuth2SuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final boolean oauthConfigured;

    public SecurityConfig(
        JwtAuthFilter jwtAuthFilter,
        OAuth2SuccessHandler oAuth2SuccessHandler,
        CorsConfigurationSource corsConfigurationSource,
        @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.corsConfigurationSource = corsConfigurationSource;
        this.oauthConfigured = googleClientId != null && !googleClientId.isBlank();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(c -> c.configurationSource(corsConfigurationSource))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Stripe Webhook は認証不要 (Stripe 署名で検証)
                .requestMatchers("/api/stripe/webhook").permitAll()
                // 認証関連 (登録・ログイン) は誰でも
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/email/**", "/api/password/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // 既存の遊びエンドポイントは誰でも (匿名プレイ可)
                .requestMatchers("/api/article/**").permitAll()
                // デイリーチャレンジは登録ユーザー限定 (カンニング対策 + Free/匿名の差別化)
                // - ランキング閲覧は誰でも可 (集客)
                // - アーカイブは controller 内で Premium チェック (402)
                .requestMatchers("/api/daily/leaderboard/**").permitAll()
                .requestMatchers("/api/daily/archive").permitAll()
                .requestMatchers("/api/daily/**").authenticated()
                .requestMatchers("/api/leaderboard/**", "/api/play/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                // コミュニティジャンルは GET/DELETE/random は誰でも、POST (作成) は controller 内で Premium チェック
                .requestMatchers("/api/community-genres/**").permitAll()
                // 認証必須
                .requestMatchers("/api/subscription/plans").permitAll()
                .requestMatchers("/api/auth/me", "/api/subscription/me", "/api/subscription/checkout", "/api/subscription/portal").authenticated()
                .requestMatchers("/api/friends/**", "/api/challenges/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(h -> h.frameOptions(fo -> fo.disable())); // H2 console 用

        // Google OAuth は環境変数が設定されている時のみ有効化
        if (oauthConfigured) {
            http.oauth2Login(o -> o.successHandler(oAuth2SuccessHandler));
        }
        return http.build();
    }
}
