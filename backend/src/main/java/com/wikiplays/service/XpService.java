package com.wikiplays.service;

import com.wikiplays.entity.User;
import com.wikiplays.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * プレイヤー経験値 (XP) とレベルを扱う。
 *
 *  - 獲得 XP / プレイ = 10 + floor(score / 50)
 *      例: score 0 → 10, score 2500 → 60, score 5000 (満点 5 問 × 1000) → 110
 *  - デイリーキャップ = 300 XP/日 (Asia/Tokyo 基準で 0:00 にリセット)
 *  - レベル L → L+1 に必要な XP = 50 + 50 * L
 *      Lv1→2: 100, Lv5→6: 300, Lv10→11: 550
 *  - レベル L 開始時点の累計 XP = 25 * (L - 1) * (L + 2)
 *      Lv1: 0, Lv2: 100, Lv3: 250, Lv10: 2700, Lv30: 23200
 *
 * レベルは XP から導出するため DB には保持しない。
 */
@Service
public class XpService {

    public static final int DAILY_CAP = 300;
    private static final ZoneId TZ = ZoneId.of("Asia/Tokyo");

    private final UserRepository userRepository;

    public XpService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** ID からユーザーを取得 (無ければ null)。 */
    public User loadUser(Long id) {
        return id == null ? null : userRepository.findById(id).orElse(null);
    }

    /** プレイ結果から得られる XP 量 (キャップ前)。 */
    public static int xpForPlay(int score) {
        return 10 + Math.max(0, score) / 50;
    }

    /** レベル L 開始時点の累計 XP。 */
    public static long xpForLevelStart(int level) {
        if (level <= 1) return 0L;
        long L = level;
        return 25L * (L - 1L) * (L + 2L);
    }

    /** 累計 XP から現在のレベルを導出 (最低 Lv1)。 */
    public static int levelFromXp(long xp) {
        if (xp <= 0L) return 1;
        // L^2 + L - 2 <= xp / 25  →  二次方程式で近似してから境界補正
        double v = 9.0 + 4.0 * xp / 25.0;
        int L = (int) Math.floor((-1.0 + Math.sqrt(v)) / 2.0);
        if (L < 1) L = 1;
        while (xpForLevelStart(L + 1) <= xp) L++;
        while (L > 1 && xpForLevelStart(L) > xp) L--;
        return L;
    }

    /** XP / レベル情報のスナップショット (API レスポンス向け)。 */
    public record XpInfo(
        long xp,
        int level,
        long xpIntoLevel,
        long xpForNextLevel,
        int dailyRemaining,
        int streakDays
    ) {}

    public XpInfo describe(User user) {
        long xp = user.getXp();
        int level = levelFromXp(xp);
        long base = xpForLevelStart(level);
        long next = xpForLevelStart(level + 1);
        int dailyRemaining = remainingDailyCap(user);
        return new XpInfo(xp, level, xp - base, next - base, dailyRemaining, currentStreak(user));
    }

    /** プレイ結果に対する XP を付与。匿名 (null) は noop。返り値は加算後のスナップショット。 */
    @Transactional
    public AwardResult award(User user, int score) {
        if (user == null) return null;
        resetIfNewDay(user);
        updateStreak(user);

        int requested = xpForPlay(score);
        int remainingCap = Math.max(0, DAILY_CAP - user.getXpEarnedToday());
        int gained = Math.min(requested, remainingCap);

        int oldLevel = levelFromXp(user.getXp());
        user.setXp(user.getXp() + gained);
        user.setXpEarnedToday(user.getXpEarnedToday() + gained);
        userRepository.save(user);

        XpInfo info = describe(user);
        boolean leveledUp = info.level() > oldLevel;
        boolean capped = gained < requested;
        return new AwardResult(gained, capped, leveledUp, info);
    }

    public record AwardResult(int gained, boolean capped, boolean leveledUp, XpInfo info) {}

    private void resetIfNewDay(User user) {
        LocalDate today = LocalDate.now(TZ);
        if (user.getXpDay() == null || !today.equals(user.getXpDay())) {
            user.setXpEarnedToday(0);
            user.setXpDay(today);
        }
    }

    /** 連続プレイ日数を更新する。昨日プレイしていれば +1、空いていれば 1 に戻す。 */
    private void updateStreak(User user) {
        LocalDate today = LocalDate.now(TZ);
        LocalDate last = user.getLastPlayDate();
        if (today.equals(last)) return;
        if (last != null && today.minusDays(1).equals(last)) {
            user.setStreakDays(user.getStreakDays() + 1);
        } else {
            user.setStreakDays(1);
        }
        user.setLastPlayDate(today);
    }

    /** 表示用の連続日数。昨日までで途切れていれば 0。 */
    public int currentStreak(User user) {
        LocalDate today = LocalDate.now(TZ);
        LocalDate last = user.getLastPlayDate();
        if (last == null) return 0;
        if (today.equals(last) || today.minusDays(1).equals(last)) return user.getStreakDays();
        return 0;
    }

    private int remainingDailyCap(User user) {
        LocalDate today = LocalDate.now(TZ);
        if (user.getXpDay() == null || !today.equals(user.getXpDay())) {
            return DAILY_CAP;
        }
        return Math.max(0, DAILY_CAP - user.getXpEarnedToday());
    }
}
