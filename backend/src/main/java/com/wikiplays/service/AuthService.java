package com.wikiplays.service;

import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
import com.wikiplays.repository.SubscriptionRepository;
import com.wikiplays.repository.UserRepository;
import com.wikiplays.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
        UserRepository userRepository,
        SubscriptionRepository subscriptionRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
    }

    /** 登録結果 (新規ユーザーと JWT を含む)。 */
    public record AuthResult(User user, String token) {}

    @Transactional
    public AuthResult register(String email, String password, String displayName, String legacyPlayerId) {
        validateEmail(email);
        validatePassword(password);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("このメールアドレスは既に登録されています");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? email.split("@")[0] : displayName);
        user.setRole("USER");
        user.setEmailVerified(false);
        user.setLegacyPlayerId(legacyPlayerId);
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // デフォルトで FREE プランの subscription を作成
        Subscription sub = new Subscription();
        sub.setUserId(user.getId());
        sub.setPlan("FREE");
        sub.setStatus("ACTIVE");
        sub.setCreatedAt(Instant.now());
        subscriptionRepository.save(sub);

        // 検証メール送信 (失敗してもユーザー登録は成功扱い)
        try {
            emailVerificationService.issueVerification(user);
        } catch (Exception ignored) {}

        return new AuthResult(user, jwtService.generateToken(user.getId(), user.getEmail()));
    }

    @Transactional
    public AuthResult login(String email, String password) {
        Optional<User> opt = userRepository.findByEmail(email);
        if (opt.isEmpty() || !passwordEncoder.matches(password, opt.get().getPasswordHash())) {
            throw new IllegalArgumentException("メールアドレスまたはパスワードが違います");
        }
        User user = opt.get();
        user.setLastLoginAt(Instant.now());
        return new AuthResult(user, jwtService.generateToken(user.getId(), user.getEmail()));
    }

    private void validateEmail(String email) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("パスワードは 8 文字以上にしてください");
        }
    }
}
