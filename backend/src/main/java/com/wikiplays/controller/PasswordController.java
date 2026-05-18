package com.wikiplays.controller;

import com.wikiplays.service.EmailVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/password")
public class PasswordController {

    private final EmailVerificationService service;

    public PasswordController(EmailVerificationService service) {
        this.service = service;
    }

    /** リセットメール送信を要求 (存在しないアドレスでも 200 を返す)。 */
    @PostMapping("/forgot")
    public ResponseEntity<Map<String, Boolean>> forgot(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email != null) service.issuePasswordReset(email);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** トークンと新パスワードでパスワードを更新。 */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Boolean>> reset(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("password");
        boolean ok = service.resetPassword(token, newPassword);
        return ok ? ResponseEntity.ok(Map.of("ok", true)) : ResponseEntity.badRequest().body(Map.of("ok", false));
    }
}
