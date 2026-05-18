package com.wikiplays.controller;

import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
import com.wikiplays.repository.SubscriptionRepository;
import com.wikiplays.service.StripeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
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
        boolean trialEligible = stripeService.isTrialEligible(user);
        Optional<Subscription> sub = subscriptionRepository.findByUserId(user.getId());
        if (sub.isEmpty()) {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("plan", "FREE");
            body.put("status", "ACTIVE");
            body.put("premiumActive", false);
            body.put("trialing", false);
            body.put("trialEligible", trialEligible);
            return ResponseEntity.ok(body);
        }
        Subscription s = sub.get();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("plan", s.getPlan());
        body.put("status", s.getStatus());
        body.put("premiumActive", s.isPremiumActive());
        body.put("trialing", s.isTrialing());
        body.put("trialEligible", trialEligible);
        body.put("currentPeriodEnd", s.getCurrentPeriodEnd() != null ? s.getCurrentPeriodEnd() : Instant.EPOCH);
        body.put("trialEnd", s.getTrialEnd() != null ? s.getTrialEnd() : Instant.EPOCH);
        body.put("cancelledAt", s.getCancelledAt() != null ? s.getCancelledAt() : Instant.EPOCH);
        return ResponseEntity.ok(body);
    }

    /** 利用可能なプラン一覧 (UI 表示用)。 */
    @GetMapping("/api/subscription/plans")
    public ResponseEntity<?> plans() {
        return ResponseEntity.ok(Map.of("plans", stripeService.getAvailablePlans()));
    }

    /** プレミアムプランへのアップグレード用 Checkout URL を返す。 */
    @PostMapping("/api/subscription/checkout")
    public ResponseEntity<?> checkout(
        @RequestParam(value = "plan", defaultValue = "1m") String plan,
        Authentication auth
    ) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        try {
            String url = stripeService.createCheckoutSession(user, plan);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("checkout session error", e);
            return ResponseEntity.status(500).body(Map.of("message", "Checkout 作成に失敗しました"));
        }
    }

    /** Stripe から最新のサブスク状態を引いて DB を同期する。
     *  Webhook 届かなかった等の救済策。冪等。 */
    @PostMapping("/api/subscription/sync")
    public ResponseEntity<?> sync(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        try {
            boolean hasActive = stripeService.syncFromStripe(user);
            return ResponseEntity.ok(Map.of("synced", true, "hasActiveSubscription", hasActive));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("sync error", e);
            return ResponseEntity.status(500).body(Map.of("message", "同期に失敗しました"));
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
