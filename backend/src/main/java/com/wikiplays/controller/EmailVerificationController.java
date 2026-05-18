package com.wikiplays.controller;

import com.wikiplays.service.EmailVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/email")
public class EmailVerificationController {

    private final EmailVerificationService service;

    public EmailVerificationController(EmailVerificationService service) {
        this.service = service;
    }

    /** メール検証トークンを使って verified フラグを立てる。 */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Boolean>> verify(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null) return ResponseEntity.badRequest().body(Map.of("ok", false));
        boolean ok = service.verifyEmail(token);
        return ok ? ResponseEntity.ok(Map.of("ok", true)) : ResponseEntity.badRequest().body(Map.of("ok", false));
    }
}
