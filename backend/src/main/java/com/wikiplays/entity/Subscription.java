package com.wikiplays.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * ユーザーのサブスクリプション状態。
 * Stripe を Source of Truth とし、Webhook で受け取った状態を反映する。
 */
@Entity
@Table(
    name = "subscription",
    uniqueConstraints = @UniqueConstraint(name = "uq_subscription_user", columnNames = "user_id"),
    indexes = {
        @Index(name = "idx_subscription_stripe_sub", columnList = "stripe_subscription_id"),
        @Index(name = "idx_subscription_stripe_customer", columnList = "stripe_customer_id")
    }
)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** "FREE" / "PREMIUM" */
    @Column(nullable = false, length = 16)
    private String plan = "FREE";

    /** "ACTIVE" / "CANCELLED" / "EXPIRED" / "PAST_DUE" / "INCOMPLETE" */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "stripe_customer_id", length = 64)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 64)
    private String stripeSubscriptionId;

    /** サブスク有効期限 (次回更新タイミング)。FREE プランの場合は null。 */
    @Column
    private Instant currentPeriodEnd;

    /** ユーザーが解約を依頼した時刻。期間終了までは有効。 */
    @Column
    private Instant cancelledAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** Premium が有効期間内か。 */
    public boolean isPremiumActive() {
        if (!"PREMIUM".equals(plan)) return false;
        if (!"ACTIVE".equals(status) && !"CANCELLED".equals(status)) return false;
        if (currentPeriodEnd == null) return "ACTIVE".equals(status);
        return Instant.now().isBefore(currentPeriodEnd);
    }
}
