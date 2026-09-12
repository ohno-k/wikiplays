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
    private final boolean h2ConsoleEnabled;

    public SecurityConfig(
        JwtAuthFilter jwtAuthFilter,
        OAuth2SuccessHandler oAuth2SuccessHandler,
        CorsConfigurationSource corsConfigurationSource,
        @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId,
        @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.corsConfigurationSource = corsConfigurationSource;
        this.oauthConfigured = googleClientId != null && !googleClientId.isBlank();
        this.h2ConsoleEnabled = h2ConsoleEnabled;
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
            .authorizeHttpRequests(auth -> {
                // Stripe Webhook は認証不要 (Stripe 署名で検証)
                auth.requestMatchers("/api/stripe/webhook").permitAll();
                // 認証関連 (登録・ログイン) は誰でも
                auth.requestMatchers("/api/auth/register", "/api/auth/login").permitAll();
                auth.requestMatchers("/api/email/**", "/api/password/**").permitAll();
                auth.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();
                // 遊びエンドポイントは匿名プレイ可 (Free 上限・Premium 判定は controller 側)
                auth.requestMatchers("/api/article/**", "/api/game/**").permitAll();
                // デイリーチャレンジは登録ユーザー限定。ランキング閲覧だけは誰でも (集客)
                auth.requestMatchers("/api/daily/leaderboard/**").permitAll();
                auth.requestMatchers("/api/daily/**").authenticated();
                auth.requestMatchers("/api/leaderboard/**", "/api/play/**").permitAll();
                // コミュニティジャンルは一覧は誰でも、作成/プレイは controller 内で Premium チェック
                auth.requestMatchers("/api/community-genres/**").permitAll();
                auth.requestMatchers("/api/subscription/plans").permitAll();
                auth.requestMatchers("/api/subscription/**", "/api/auth/me").authenticated();
                auth.requestMatchers("/api/friends/**", "/api/challenges/**").authenticated();
                if (h2ConsoleEnabled) auth.requestMatchers("/h2-console/**").permitAll();
                // エラーページへの内部フォワードは拒否しない (denyAll だと 403 に化けてしまう)
                auth.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR, jakarta.servlet.DispatcherType.FORWARD).permitAll();
                auth.requestMatchers("/error").permitAll();
                // 上に列挙していないものは既定で拒否 (新しいエンドポイントは明示的に開ける)
                auth.anyRequest().denyAll();
            })
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // H2 コンソール (ローカルのみ) は iframe を使うので frameOptions を緩める
        if (h2ConsoleEnabled) {
            http.headers(h -> h.frameOptions(fo -> fo.sameOrigin()));
        }

        // Google OAuth は環境変数が設定されている時のみ有効化
        if (oauthConfigured) {
            http.oauth2Login(o -> o.successHandler(oAuth2SuccessHandler));
        }
        return http.build();
    }
}
