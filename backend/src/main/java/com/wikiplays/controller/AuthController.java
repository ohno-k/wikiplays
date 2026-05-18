package com.wikiplays.controller;

import com.wikiplays.dto.AuthRequest;
import com.wikiplays.dto.AuthResponse;
import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
import com.wikiplays.repository.SubscriptionRepository;
import com.wikiplays.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:4173"})
public class AuthController {

    private final AuthService authService;
    private final SubscriptionRepository subscriptionRepository;

    public AuthController(AuthService authService, SubscriptionRepository subscriptionRepository) {
        this.authService = authService;
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest req) {
        try {
            AuthService.AuthResult result = authService.register(
                req.email(), req.password(), req.displayName(), req.legacyPlayerId()
            );
            return ResponseEntity.ok(toResponse(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorBody(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        try {
            AuthService.AuthResult result = authService.login(req.email(), req.password());
            return ResponseEntity.ok(toResponse(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(new ErrorBody(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        Optional<Subscription> sub = subscriptionRepository.findByUserId(user.getId());
        String plan = sub.map(Subscription::getPlan).orElse("FREE");
        boolean premiumActive = sub.map(Subscription::isPremiumActive).orElse(false);
        return ResponseEntity.ok(new AuthResponse.UserInfo(
            user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), plan, premiumActive
        ));
    }

    private AuthResponse toResponse(AuthService.AuthResult result) {
        User u = result.user();
        Optional<Subscription> sub = subscriptionRepository.findByUserId(u.getId());
        String plan = sub.map(Subscription::getPlan).orElse("FREE");
        boolean premiumActive = sub.map(Subscription::isPremiumActive).orElse(false);
        return new AuthResponse(
            result.token(),
            new AuthResponse.UserInfo(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), plan, premiumActive)
        );
    }

    private record ErrorBody(String message) {}
}
