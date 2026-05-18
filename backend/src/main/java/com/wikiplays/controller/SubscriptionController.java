package com.wikiplays.controller;

import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
import com.wikiplays.repository.SubscriptionRepository;
import com.wikiplays.service.StripeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:4173"})
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final StripeService stripeService;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionController(StripeService stripeService, SubscriptionRepository subscriptionRepository) {
        this.stripeService = stripeService;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** 現在のサブスク状態を返す。 */
    @GetMapping("/api/subscription/me")
    public ResponseEntity<?> mine(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        Optional<Subscription> sub = subscriptionRepository.findByUserId(user.getId());
        if (sub.isEmpty()) {
            return ResponseEntity.ok(Map.of("plan", "FREE", "status", "ACTIVE", "premiumActive", false));
        }
        Subscription s = sub.get();
        return ResponseEntity.ok(Map.of(
            "plan", s.getPlan(),
            "status", s.getStatus(),
            "premiumActive", s.isPremiumActive(),
            "currentPeriodEnd", s.getCurrentPeriodEnd() != null ? s.getCurrentPeriodEnd() : Instant.EPOCH,
            "cancelledAt", s.getCancelledAt() != null ? s.getCancelledAt() : Instant.EPOCH
        ));
    }

    /** Premium プランへのアップグレード用 Checkout URL を返す。 */
    @PostMapping("/api/subscription/checkout")
    public ResponseEntity<?> checkout(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        try {
            String url = stripeService.createCheckoutSession(user);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("checkout session error", e);
            return ResponseEntity.status(500).body(Map.of("message", "Checkout 作成に失敗しました"));
        }
    }

    /** Stripe Customer Portal セッション URL (解約・支払い管理)。 */
    @PostMapping("/api/subscription/portal")
    public ResponseEntity<?> portal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        try {
            String url = stripeService.createPortalSession(user);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("portal session error", e);
            return ResponseEntity.status(500).body(Map.of("message", "Portal 作成に失敗しました"));
        }
    }

    /** Stripe Webhook 受信。署名検証は StripeService で実施。 */
    @PostMapping("/api/stripe/webhook")
    public ResponseEntity<String> webhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader
    ) {
        try {
            stripeService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.warn("webhook handling failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("error");
        }
    }
}
