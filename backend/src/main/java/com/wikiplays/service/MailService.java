package com.wikiplays.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * メール送信サービス。
 * SMTP 未設定の場合はログにメッセージを出力するだけ (開発時の確認用)。
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendUrl;
    private final boolean enabled;

    public MailService(
        JavaMailSender mailSender,
        @Value("${wikiplays.mail.from:no-reply@wikiplays.me}") String fromAddress,
        @Value("${wikiplays.frontend-url:http://localhost:5173}") String frontendUrl,
        @Value("${MAIL_HOST:}") String mailHostEnv
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
        // 実 SMTP は環境変数 MAIL_HOST が明示的に設定された時のみ有効
        // (application-local.yml の dummy localhost は無効扱い)
        this.enabled = mailHostEnv != null && !mailHostEnv.isBlank();
    }

    public void sendVerification(String to, String userName, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String subject = "[Wikiplays] メールアドレスの確認";
        String body = String.format(
            "%s 様%n%n" +
            "Wikiplays にご登録いただきありがとうございます。%n" +
            "以下の URL にアクセスして、メールアドレスの確認を完了してください。%n%n" +
            "%s%n%n" +
            "このリンクは 24 時間有効です。心当たりがない場合はこのメールを破棄してください。",
            userName, link
        );
        send(to, subject, body);
    }

    public void sendPasswordReset(String to, String userName, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String subject = "[Wikiplays] パスワード再設定";
        String body = String.format(
            "%s 様%n%n" +
            "パスワード再設定のリクエストを受け付けました。%n" +
            "以下の URL にアクセスして、新しいパスワードを設定してください。%n%n" +
            "%s%n%n" +
            "このリンクは 1 時間有効です。心当たりがない場合はこのメールを破棄してください。",
            userName, link
        );
        send(to, subject, body);
    }

    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("[MailService disabled] to={} subject={}\n{}", to, subject, body);
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            mailSender.send(mime);
        } catch (MessagingException e) {
            log.error("メール送信失敗 to={}", to, e);
            // 送信失敗は fail-silent (登録自体は成功させる)
        }
    }

    /** SMTP 設定済みなら true。フロントでメール機能の有無を表示する用 */
    public boolean isEnabled() { return enabled; }
}
