package com.wikiplays.security;

import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
import com.wikiplays.repository.SubscriptionRepository;
import com.wikiplays.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * Google OAuth 認証成功時のハンドラ。
 * - User が未存在なら作成
 * - JWT を発行してフロントの /oauth-callback に token をクエリで渡す
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final JwtService jwtService;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
        UserRepository userRepository,
        SubscriptionRepository subscriptionRepository,
        JwtService jwtService,
        @Value("${wikiplays.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.jwtService = jwtService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        if (email == null) {
            response.sendRedirect(frontendUrl + "/login?error=no_email");
            return;
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            // OAuth ユーザーはパスワード持たないので、ランダムハッシュを設定
            u.setPasswordHash("$2a$10$oauth-no-password-set-" + System.nanoTime());
            u.setDisplayName(name != null && !name.isBlank() ? name : email.split("@")[0]);
            u.setRole("USER");
            u.setEmailVerified(true); // Google 認証ならメール確認済み
            u.setCreatedAt(Instant.now());
            u.setLastLoginAt(Instant.now());
            User saved = userRepository.save(u);
            // FREE サブスクを作る
            Subscription sub = new Subscription();
            sub.setUserId(saved.getId());
            sub.setPlan("FREE");
            sub.setStatus("ACTIVE");
            sub.setCreatedAt(Instant.now());
            subscriptionRepository.save(sub);
            return saved;
        });

        user.setLastLoginAt(Instant.now());
        if (!user.isEmailVerified()) user.setEmailVerified(true);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        String redirect = frontendUrl + "/oauth-callback?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        response.sendRedirect(redirect);
    }
}
