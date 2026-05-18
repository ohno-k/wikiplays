package com.wikiplays.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerCollection;
import com.stripe.model.Event;
import com.stripe.model.SubscriptionCollection;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerListParams;
import com.stripe.param.SubscriptionListParams;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stripe との連携。
 * Checkout Session の作成、Webhook の受信、サブスク状態の更新を担う。
 *
 * プラン:
 * - "1m": 月額 ¥500
 * - "3m": 3 ヶ月 ¥1,300 (割引)
 * - "6m": 6 ヶ月 ¥2,000 (大きな割引)
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final String secretKey;
    private final String webhookSecret;
    private final Map<String, String> priceIds; // plan key -> Stripe Price ID
    private final String frontendUrl;

    public StripeService(
        UserRepository userRepository,
        SubscriptionRepository subscriptionRepository,
        @Value("${wikiplays.stripe.secret-key:}") String secretKey,
        @Value("${wikiplays.stripe.webhook-secret:}") String webhookSecret,
        @Value("${wikiplays.stripe.price-id-1m:}") String priceId1m,
        @Value("${wikiplays.stripe.price-id-3m:}") String priceId3m,
        @Value("${wikiplays.stripe.price-id-6m:}") String priceId6m,
        @Value("${wikiplays.stripe.price-id-premium:}") String legacyPriceId,
        @Value("${wikiplays.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.frontendUrl = frontendUrl;

        this.priceIds = new HashMap<>();
        if (!priceId1m.isBlank()) priceIds.put("1m", priceId1m);
        if (!priceId3m.isBlank()) priceIds.put("3m", priceId3m);
        if (!priceId6m.isBlank()) priceIds.put("6m", priceId6m);
        // 互換性: 旧 PRICE_ID_PREMIUM が設定されているが新しい 1m が空ならそれを 1m として使う
        if (priceIds.get("1m") == null && !legacyPriceId.isBlank()) {
            priceIds.put("1m", legacyPriceId);
        }

        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    /** 利用可能なプランの key 一覧。 */
    public java.util.Set<String> getAvailablePlans() {
        return priceIds.keySet();
    }

    /** 初回トライアルの日数 (7 日)。 */
    private static final long TRIAL_PERIOD_DAYS = 7;

    /**
     * 指定プランの Checkout Session を作成し、リダイレクト先 URL を返す。
     * plan: "1m" / "3m" / "6m" のいずれか。
     * 初回のみ 7 日間トライアル付き。過去に Stripe サブスクを作成したことがある
     * ユーザー (stripe_subscription_id が設定されている) はトライアル対象外。
     */
    public String createCheckoutSession(User user, String plan) throws StripeException {
        ensureConfigured();
        String priceId = priceIds.get(plan);
        if (priceId == null) {
            throw new IllegalArgumentException("不明なプラン: " + plan + " (利用可能: " + priceIds.keySet() + ")");
        }

        Optional<Subscription> subOpt = subscriptionRepository.findByUserId(user.getId());
        String customerId = subOpt.map(Subscription::getStripeCustomerId).orElse(null);
        boolean trialEligible = subOpt
            .map(s -> s.getStripeSubscriptionId() == null || s.getStripeSubscriptionId().isBlank())
            .orElse(true);

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(frontendUrl + "/account?subscribed=1")
            .setCancelUrl(frontendUrl + "/account?cancelled=1")
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setPrice(priceId)
                .setQuantity(1L)
                .build())
            .putMetadata("user_id", String.valueOf(user.getId()))
            .putMetadata("plan", plan);

        if (trialEligible) {
            builder.setSubscriptionData(
                SessionCreateParams.SubscriptionData.builder()
                    .setTrialPeriodDays(TRIAL_PERIOD_DAYS)
                    .build()
            );
        }

        if (customerId != null && !customerId.isBlank()) {
            builder.setCustomer(customerId);
        } else {
            builder.setCustomerEmail(user.getEmail());
        }

        Session session = Session.create(builder.build());
        return session.getUrl();
    }

    /**
     * Stripe から最新のサブスクリプション状態を取得し、ローカル DB を同期する。
     * Webhook が届かなかった等で local 状態が古い場合の救済策。
     *
     * 動作:
     *  1. ユーザーのメールアドレスで Stripe Customer を検索
     *  2. その Customer のアクティブ (active/trialing/past_due) なサブスクを探す
     *  3. Subscription エンティティを upsert
     *
     * セキュリティ: 認証済みユーザーの email でのみ検索するため、他人の Stripe
     * アカウントを取得することはない (email 認証必須なので email の所有者が確定済)。
     */
    @Transactional
    public boolean syncFromStripe(User user) throws StripeException {
        ensureConfigured();

        // 既存の Subscription レコードがあれば customerId を再利用
        Optional<Subscription> existing = subscriptionRepository.findByUserId(user.getId());
        String customerId = existing.map(Subscription::getStripeCustomerId).filter(s -> !s.isBlank()).orElse(null);

        // customerId 未保存なら email から探す
        if (customerId == null) {
            CustomerListParams listParams = CustomerListParams.builder()
                .setEmail(user.getEmail())
                .setLimit(1L)
                .build();
            CustomerCollection customers = Customer.list(listParams);
            if (!customers.getData().isEmpty()) {
                customerId = customers.getData().get(0).getId();
            }
        }

        if (customerId == null) {
            log.info("syncFromStripe: no Stripe customer for user {}", user.getId());
            return false;
        }

        // Customer の全サブスクから「active / trialing / past_due」のものを探す
        SubscriptionListParams subParams = SubscriptionListParams.builder()
            .setCustomer(customerId)
            .setStatus(SubscriptionListParams.Status.ALL)
            .setLimit(10L)
            .build();
        SubscriptionCollection subs = com.stripe.model.Subscription.list(subParams);

        com.stripe.model.Subscription activeSub = null;
        for (com.stripe.model.Subscription s : subs.getData()) {
            String status = s.getStatus();
            if ("active".equals(status) || "trialing".equals(status) || "past_due".equals(status)) {
                activeSub = s;
                break;
            }
        }

        // ローカル Subscription を upsert
        Subscription sub = existing.orElseGet(() -> {
            Subscription s = new Subscription();
            s.setUserId(user.getId());
            s.setCreatedAt(Instant.now());
            return s;
        });
        sub.setStripeCustomerId(customerId);

        if (activeSub != null) {
            sub.setStripeSubscriptionId(activeSub.getId());
            sub.setPlan("PREMIUM");
            sub.setStatus(mapStripeStatus(activeSub.getStatus()));
            if (activeSub.getCurrentPeriodEnd() != null) {
                sub.setCurrentPeriodEnd(Instant.ofEpochSecond(activeSub.getCurrentPeriodEnd()));
            }
            if (activeSub.getTrialEnd() != null) {
                sub.setTrialEnd(Instant.ofEpochSecond(activeSub.getTrialEnd()));
            } else {
                sub.setTrialEnd(null);
            }
            sub.setCancelledAt(Boolean.TRUE.equals(activeSub.getCancelAtPeriodEnd()) ? Instant.now() : null);
        } else {
            // アクティブなサブスクなし
            sub.setPlan("FREE");
            sub.setStatus("ACTIVE");
            sub.setTrialEnd(null);
            sub.setCurrentPeriodEnd(null);
        }
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
        log.info("syncFromStripe: synced user {} -> plan={}, status={}",
            user.getId(), sub.getPlan(), sub.getStatus());
        return activeSub != null;
    }

    /** ユーザーが今 (新規) Checkout で 7 日間トライアルの対象かどうか。 */
    public boolean isTrialEligible(User user) {
        return subscriptionRepository.findByUserId(user.getId())
            .map(s -> s.getStripeSubscriptionId() == null || s.getStripeSubscriptionId().isBlank())
            .orElse(true);
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
            case "customer.subscription.trial_will_end" -> handleTrialWillEnd(event);
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
        // トライアル終了時刻 (trialing でなくなれば null になる)
        if (stripeSub.getTrialEnd() != null) {
            sub.setTrialEnd(Instant.ofEpochSecond(stripeSub.getTrialEnd()));
        } else {
            sub.setTrialEnd(null);
        }
        if (Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd())) {
            sub.setCancelledAt(Instant.now());
        }
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
    }

    /** トライアル終了 3 日前の Webhook。現状はログのみ。
     *  将来的にはここで「もうすぐ課金開始」メール送信を実装する。 */
    private void handleTrialWillEnd(Event event) {
        Optional<com.stripe.model.Subscription> stripeSubOpt = event.getDataObjectDeserializer().getObject()
            .filter(o -> o instanceof com.stripe.model.Subscription)
            .map(o -> (com.stripe.model.Subscription) o);
        if (stripeSubOpt.isEmpty()) return;
        com.stripe.model.Subscription stripeSub = stripeSubOpt.get();
        log.info("Stripe trial_will_end: subscription={}, trialEnd={}",
            stripeSub.getId(), stripeSub.getTrialEnd());
        // 既存の Subscription レコードも更新しておく
        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            if (stripeSub.getTrialEnd() != null) {
                sub.setTrialEnd(Instant.ofEpochSecond(stripeSub.getTrialEnd()));
                sub.setUpdatedAt(Instant.now());
                subscriptionRepository.save(sub);
            }
        });
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
        // Stripe 側で自動リトライされる。
    }

    private String mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> "ACTIVE";
            case "trialing" -> "TRIALING";
            case "canceled" -> "EXPIRED";
            case "past_due", "unpaid" -> "PAST_DUE";
            case "incomplete", "incomplete_expired" -> "INCOMPLETE";
            default -> stripeStatus.toUpperCase();
        };
    }

    private void ensureConfigured() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe secret key 未設定です (STRIPE_SECRET_KEY)");
        }
        if (priceIds.isEmpty()) {
            throw new IllegalStateException("Stripe Price ID が一つも設定されていません (STRIPE_PRICE_ID_1M 等)");
        }
    }
}
