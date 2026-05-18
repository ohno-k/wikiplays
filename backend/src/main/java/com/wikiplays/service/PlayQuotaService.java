package com.wikiplays.service;

import com.wikiplays.dto.PlayQuotaResponse;
import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
import com.wikiplays.repository.PlayRecordRepository;
import com.wikiplays.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Free プランの 1 日プレイ制限を判定するサービス。
 * Premium は無制限、Free と匿名は 1 日 5 問まで。
 */
@Service
public class PlayQuotaService {

    private static final ZoneId TZ = ZoneId.of("Asia/Tokyo");
    private static final int FREE_DAILY_LIMIT = 5;
    private static final String LIMITED_MODE = "a";

    private final PlayRecordRepository playRecordRepository;
    private final SubscriptionRepository subscriptionRepository;

    public PlayQuotaService(
        PlayRecordRepository playRecordRepository,
        SubscriptionRepository subscriptionRepository
    ) {
        this.playRecordRepository = playRecordRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    public PlayQuotaResponse check(User user, String playerId) {
        boolean unlimited = isPremium(user);
        Instant todayStart = LocalDate.now(TZ).atStartOfDay(TZ).toInstant();
        long played = playRecordRepository.countSince(
            user != null ? user.getId() : null,
            user == null ? playerId : null,
            LIMITED_MODE,
            todayStart
        );
        if (unlimited) {
            return new PlayQuotaResponse(true, played, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        int remaining = (int) Math.max(0, FREE_DAILY_LIMIT - played);
        return new PlayQuotaResponse(false, played, FREE_DAILY_LIMIT, remaining);
    }

    public boolean canPlay(User user, String playerId) {
        return check(user, playerId).remaining() > 0;
    }

    private boolean isPremium(User user) {
        if (user == null) return false;
        Optional<Subscription> sub = subscriptionRepository.findByUserId(user.getId());
        return sub.map(Subscription::isPremiumActive).orElse(false);
    }
}
