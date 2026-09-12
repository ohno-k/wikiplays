package com.wikiplays.service;

import com.wikiplays.entity.User;
import com.wikiplays.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class XpServiceTest {

    private final XpService xp = new XpService(Mockito.mock(UserRepository.class));

    @Test
    void levelFormulaIsConsistent() {
        assertEquals(1, XpService.levelFromXp(0));
        assertEquals(1, XpService.levelFromXp(99));
        assertEquals(2, XpService.levelFromXp(100));
        assertEquals(10, XpService.levelFromXp(2700));
        for (int level = 1; level < 60; level++) {
            long start = XpService.xpForLevelStart(level);
            assertEquals(level, XpService.levelFromXp(start));
            assertEquals(level, XpService.levelFromXp(XpService.xpForLevelStart(level + 1) - 1));
        }
    }

    @Test
    void awardAppliesDailyCapAndStreak() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        User u = new User();
        u.setXp(0);
        u.setLastPlayDate(today.minusDays(1));
        u.setStreakDays(3);

        XpService.AwardResult r = xp.award(u, 5000);
        assertEquals(110, r.gained());
        assertEquals(4, r.info().streakDays());

        // 同日 2 回目はストリークが増えない
        xp.award(u, 5000);
        assertEquals(4, u.getStreakDays());

        // キャップ 300 に到達
        XpService.AwardResult r3 = xp.award(u, 5000);
        assertTrue(r3.capped());
        assertEquals(300, u.getXpEarnedToday());
    }

    @Test
    void streakResetsAfterGap() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        User u = new User();
        u.setLastPlayDate(today.minusDays(3));
        u.setStreakDays(10);
        assertEquals(0, xp.currentStreak(u));
        xp.award(u, 0);
        assertEquals(1, u.getStreakDays());
    }
}
