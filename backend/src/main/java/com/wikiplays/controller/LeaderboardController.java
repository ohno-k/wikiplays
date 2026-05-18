package com.wikiplays.controller;

import com.wikiplays.dto.LeaderboardEntry;
import com.wikiplays.repository.PlayRecordRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private static final ZoneId TZ = ZoneId.of("Asia/Tokyo");

    private final PlayRecordRepository repository;

    public LeaderboardController(PlayRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * ランキング取得。
     * period: today / week / month / all (デフォルト all)
     * genre, scope, mode, communityGenreId は任意 (絞り込み)
     */
    @GetMapping
    public ResponseEntity<List<LeaderboardEntry>> get(
        @RequestParam(value = "period", defaultValue = "all") String period,
        @RequestParam(value = "mode", required = false) String mode,
        @RequestParam(value = "genre", required = false) String genre,
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "communityGenreId", required = false) Long communityGenreId,
        @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        Instant since = sinceFor(period);
        int safeLimit = Math.min(limit, 100);
        List<Object[]> rows = repository.aggregateLeaderboard(
            since,
            blankToNull(mode),
            blankToNull(genre),
            blankToNull(scope),
            communityGenreId,
            safeLimit
        );
        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (Object[] row : rows) {
            String name = (String) row[0];
            long total = ((Number) row[1]).longValue();
            int best = ((Number) row[2]).intValue();
            long count = ((Number) row[3]).longValue();
            entries.add(new LeaderboardEntry(rank++, name, total, best, count));
        }
        return ResponseEntity.ok(entries);
    }

    private Instant sinceFor(String period) {
        LocalDate today = LocalDate.now(TZ);
        return switch (period) {
            case "today" -> today.atStartOfDay(TZ).toInstant();
            case "week"  -> today.minusDays(6).atStartOfDay(TZ).toInstant();
            case "month" -> today.minusDays(29).atStartOfDay(TZ).toInstant();
            default      -> Instant.EPOCH.plus(1, ChronoUnit.SECONDS); // all-time
        };
    }

    private String blankToNull(String s) {
        if (s == null || s.isBlank() || "random".equals(s)) return null;
        return s;
    }
}
