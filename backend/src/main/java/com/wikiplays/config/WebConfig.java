package com.wikiplays.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS のグローバル設定。
 * 環境変数 WIKIPLAYS_ALLOWED_ORIGINS でカンマ区切りの許可 origin を指定可能。
 * 例: "https://wikiplays.me,https://www.wikiplays.me"
 *
 * ローカル開発時のデフォルトは Vite (5173) と preview (4173)。
 */
@Configuration
public class WebConfig {

    private final String allowedOriginsCsv;

    public WebConfig(
        @Value("${wikiplays.cors.allowed-origins:http://localhost:5173,http://localhost:4173}") String allowedOriginsCsv
    ) {
        this.allowedOriginsCsv = allowedOriginsCsv;
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOriginsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
