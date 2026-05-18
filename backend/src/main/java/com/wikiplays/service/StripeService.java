package com.wikiplays.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
import com.wikiplays.repository.SubscriptionRepository;
import com.wikiplays.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Stripe との連携。
 * Checkout Session の作成、Webhook の受信、サブスク状態の更新を担う。
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final String secretKey;
    private final String webhookSecret;
    private final String premiumPriceId;
    private final String frontendUrl;

    public StripeService(
        UserRepository userRepository,
        SubscriptionRepository subscriptionRepository,
        @Value("${wikiplays.stripe.secret-key:}") String secretKey,
        @Value("${wikiplays.stripe.webhook-secret:}") String webhookSecret,
        @Value("${wikiplays.stripe.price-id-premium:}") String premiumPriceId,
        @Value("${wikiplays.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.premiumPriceId = premiumPriceId;
        this.frontendUrl = frontendUrl;
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    /** Premium プランの Checkout Session を作成し、リダイレクト先 URL を返す。 */
    public String createCheckoutSession(User user) throws StripeException {
        ensureConfigured();

        // 既存の Stripe Customer ID を取得 (なければ Stripe Checkout 内で自動作成される)
        Optional<Subscription> subOpt = subscriptionRepository.findByUserId(user.getId());
        String customerId = subOpt.map(Subscription::getStripeCustomerId).orElse(null);

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(frontendUrl + "/account?subscribed=1")
            .setCancelUrl(frontendUrl + "/account?cancelled=1")
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setPrice(premiumPriceId)
                .setQuantity(1L)
                .build())
            .putMetadata("user_id", String.valueOf(user.getId()));

        if (customerId != null && !customerId.isBlank()) {
            builder.setCustomer(customerId);
        } else {
            builder.setCustomerEmail(user.getEmail());
        }

        Session session = Session.create(builder.build());
        return session.getUrl();
    }

    /** Stripe Customer Portal セッション (解約・更新管理) の URL を返す。 */
    public String createPortalSession(User user) throws StripeException {
        ensureConfigured();
        Subscription sub = subscriptionRepository.findByUserId(user.getId())
            .orElseThrow(() -> new IllegalStateException("サブスクが存在しません"));
        String customerId = sub.getStripeCustomerId();
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("Stripe Customer がまだ作成されていません");
        }
        com.stripe.param.billingportal.SessionCreateParams params =
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(frontendUrl + "/account")
                .build();
        return com.stripe.model.billingportal.Session.create(params).getUrl();
    }

    /** Webhook の生ペイロードと署名を検証し、関連 event を処理。 */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) throws Exception {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Stripe webhook secret not configured, skipping verification");
            return;
        }
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        log.info("Stripe event: {}", event.getType());

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.updated", "customer.subscription.created" -> handleSubscriptionChanged(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            default -> {} // 他は無視
        }
    }

    private void handleCheckoutCompleted(Event event) {
        Optional<Session> sessionOpt = event.getDataObjectDeserializer().getObject()
            .filter(o -> o instanceof Session)
            .map(o -> (Session) o);
        if (sessionOpt.isEmpty()) return;
        Session session = sessionOpt.get();
        String userIdStr = session.getMetadata().get("user_id");
        if (userIdStr == null) return;
        Long userId = Long.valueOf(userIdStr);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return;

        Subscription sub = subscriptionRepository.findByUserId(userId)
            .orElseGet(() -> {
                Subscription s = new Subscription();
                s.setUserId(userId);
                s.setCreatedAt(Instant.now());
                return s;
            });
        sub.setStripeCustomerId(session.getCustomer());
        sub.setStripeSubscriptionId(session.getSubscription());
        sub.setPlan("PREMIUM");
        sub.setStatus("ACTIVE");
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
    }

    private void handleSubscriptionChanged(Event event) {
        Optional<com.stripe.model.Subscription> stripeSubOpt = event.getDataObjectDeserializer().getObject()
            .filter(o -> o instanceof com.stripe.model.Subscription)
            .map(o -> (com.stripe.model.Subscription) o);
        if (stripeSubOpt.isEmpty()) return;
        com.stripe.model.Subscription stripeSub = stripeSubOpt.get();
        Optional<Subscription> ours = subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId());
        if (ours.isEmpty()) return;
        Subscription sub = ours.get();
        sub.setStatus(mapStripeStatus(stripeSub.getStatus()));
        if (stripeSub.getCurrentPeriodEnd() != null) {
            sub.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()));
        }
        if (Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd())) {
            sub.setCancelledAt(Instant.now());
        }
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
    }

    private void handleSubscriptionDeleted(Event event) {
        Optional<com.stripe.model.Subscription> stripeSubOpt = event.getDataObjectDeserializer().getObject()
            .filter(o -> o instanceof com.stripe.model.Subscription)
            .map(o -> (com.stripe.model.Subscription) o);
        if (stripeSubOpt.isEmpty()) return;
        com.stripe.model.Subscription stripeSub = stripeSubOpt.get();
        Optional<Subscription> ours = subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId());
        if (ours.isEmpty()) return;
        Subscription sub = ours.get();
        sub.setPlan("FREE");
        sub.setStatus("EXPIRED");
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
    }

    private void handlePaymentFailed(Event event) {
        // Stripe 側で自動リトライされる。状態のみ "PAST_DUE" に。
        // Subscription オブジェクトとの紐付けが必要だが今回は省略。
    }

    private String mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active", "trialing" -> "ACTIVE";
            case "canceled" -> "EXPIRED";
            case "past_due", "unpaid" -> "PAST_DUE";
            case "incomplete", "incomplete_expired" -> "INCOMPLETE";
            default -> stripeStatus.toUpperCase();
        };
    }

    private void ensureConfigured() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe secret key 未設定です (環境変数 STRIPE_SECRET_KEY)");
        }
        if (premiumPriceId == null || premiumPriceId.isBlank()) {
            throw new IllegalStateException("Stripe price ID 未設定です (環境変数 STRIPE_PRICE_ID_PREMIUM)");
        }
    }
}
