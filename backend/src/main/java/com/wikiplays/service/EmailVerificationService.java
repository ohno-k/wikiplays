package com.wikiplays.service;

import com.wikiplays.entity.EmailToken;
import com.wikiplays.entity.User;
import com.wikiplays.repository.EmailTokenRepository;
import com.wikiplays.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** メール検証およびパスワードリセットトークンの発行・検証。 */
@Service
public class EmailVerificationService {

    public static final String TYPE_VERIFICATION = "VERIFICATION";
    public static final String TYPE_PASSWORD_RESET = "PASSWORD_RESET";

    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    private final EmailTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;

    public EmailVerificationService(
        EmailTokenRepository tokenRepo,
        UserRepository userRepo,
        MailService mailService,
        PasswordEncoder passwordEncoder
    ) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 検証メールを送信する。
     * REQUIRES_NEW で親トランザクション (AuthService.register) と分離し、
     * メール送信失敗で親をロールバックさせない。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issueVerification(User user) {
        try {
            EmailToken token = createToken(user.getId(), TYPE_VERIFICATION, VERIFICATION_TTL);
            mailService.sendVerification(user.getEmail(), user.getDisplayName(), token.getToken());
        } catch (Exception ignored) {
            // メール送信失敗は無視 (登録自体は成功扱い)
        }
    }

    /** リセットメールを送信する。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issuePasswordReset(String email) {
        Optional<User> opt = userRepo.findByEmail(email);
        if (opt.isEmpty()) return; // ユーザー存在の有無を漏らさないため成功扱い
        User user = opt.get();
        EmailToken token = createToken(user.getId(), TYPE_PASSWORD_RESET, PASSWORD_RESET_TTL);
        mailService.sendPasswordReset(user.getEmail(), user.getDisplayName(), token.getToken());
    }

    @Transactional
    public boolean verifyEmail(String token) {
        Optional<EmailToken> opt = tokenRepo.findByToken(token);
        if (opt.isEmpty()) return false;
        EmailToken t = opt.get();
        if (!TYPE_VERIFICATION.equals(t.getType()) || !t.isValid()) return false;
        Optional<User> userOpt = userRepo.findById(t.getUserId());
        if (userOpt.isEmpty()) return false;
        User user = userOpt.get();
        user.setEmailVerified(true);
        t.setUsed(true);
        tokenRepo.save(t);
        return true;
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) return false;
        Optional<EmailToken> opt = tokenRepo.findByToken(token);
        if (opt.isEmpty()) return false;
        EmailToken t = opt.get();
        if (!TYPE_PASSWORD_RESET.equals(t.getType()) || !t.isValid()) return false;
        Optional<User> userOpt = userRepo.findById(t.getUserId());
        if (userOpt.isEmpty()) return false;
        User user = userOpt.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        t.setUsed(true);
        tokenRepo.save(t);
        return true;
    }

    private EmailToken createToken(Long userId, String type, Duration ttl) {
        EmailToken t = new EmailToken();
        t.setUserId(userId);
        t.setType(type);
        t.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        t.setExpiresAt(Instant.now().plus(ttl));
        t.setUsed(false);
        t.setCreatedAt(Instant.now());
        return tokenRepo.save(t);
    }
}
