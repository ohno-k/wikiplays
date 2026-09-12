package com.wikiplays.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP 単位の簡易レートリミット (固定 1 分窓)。
 * Wikipedia API を中継する記事系エンドポイントと、ブルートフォース対象の認証系を守る。
 * 単一インスタンス前提のインメモリ実装。複数台に増やすときは Redis 等に置き換える。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int ARTICLE_LIMIT_PER_MINUTE = 120;
    private static final int AUTH_LIMIT_PER_MINUTE = 20;

    private record Window(long minute, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = limitFor(path, request.getMethod());
        if (limit > 0) {
            String key = clientIp(request) + "|" + (limit == AUTH_LIMIT_PER_MINUTE ? "auth" : "play");
            long minute = System.currentTimeMillis() / 60_000L;
            Window w = windows.compute(key, (k, old) ->
                (old == null || old.minute() != minute) ? new Window(minute, new AtomicInteger()) : old);
            if (w.count().incrementAndGet() > limit) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"リクエストが多すぎます。しばらく待ってから再試行してください。\"}");
                return;
            }
            if (windows.size() > 50_000) windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        }
        chain.doFilter(request, response);
    }

    private static int limitFor(String path, String method) {
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")
            || path.startsWith("/api/password/")) {
            return "POST".equals(method) ? AUTH_LIMIT_PER_MINUTE : 0;
        }
        if (path.startsWith("/api/article/") || path.startsWith("/api/game/")
            || (path.startsWith("/api/community-genres/") && path.endsWith("/random"))) {
            return ARTICLE_LIMIT_PER_MINUTE;
        }
        return 0;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
