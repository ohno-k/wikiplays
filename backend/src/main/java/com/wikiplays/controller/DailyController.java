package com.wikiplays.controller;

import com.wikiplays.dto.DailyChallengeResponse;
import com.wikiplays.dto.DailyLeaderboardEntry;
import com.wikiplays.entity.DailyChallenge;
import com.wikiplays.entity.User;
import com.wikiplays.repository.DailyChallengeRepository;
import com.wikiplays.service.DailyChallengeService;
import com.wikiplays.service.PlayQuotaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * デイリーチャレンジの概要・ランキング・アーカイブ一覧。
 * プレイ自体 (問題配信・採点・スコア記録) は /api/game のセッション API で行う。
 */
@RestController
@RequestMapping("/api/daily")
public class DailyController {

    private final DailyChallengeService service;
    private final DailyChallengeRepository challengeRepo;
    private final PlayQuotaService quotaService;

    public DailyController(
        DailyChallengeService service,
        DailyChallengeRepository challengeRepo,
        PlayQuotaService quotaService
    ) {
        this.service = service;
        this.challengeRepo = challengeRepo;
        this.quotaService = quotaService;
    }

    /** 今日のチャレンジ概要を取得 (なければ生成)。登録ユーザー限定。 */
    @GetMapping("/today")
    public ResponseEntity<DailyChallengeResponse> today(
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "genre", required = false) String genre,
        Authentication auth
    ) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        String playerId = "u" + user.getId();
        Optional<DailyChallengeResponse> resp = service.getToday(scope, genre, playerId);
        return resp.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(503).build());
    }

    /** 過去チャレンジの概要 (Premium 限定)。 */
    @GetMapping("/challenge/{id}")
    public ResponseEntity<DailyChallengeResponse> byId(@PathVariable("id") Long id, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        if (!quotaService.isPremium(user)) return ResponseEntity.status(402).build();
        return service.getById(id, "u" + user.getId())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** ランキング取得。 */
    @GetMapping("/leaderboard/{challengeId}")
    public ResponseEntity<List<DailyLeaderboardEntry>> leaderboard(
        @PathVariable("challengeId") Long challengeId,
        @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(service.leaderboard(challengeId, limit));
    }

    /** 過去デイリーチャレンジ一覧 (Premium 限定)。記事タイトルは含めない。 */
    @GetMapping("/archive")
    public ResponseEntity<?> archive(
        @RequestParam(value = "limit", defaultValue = "30") int limit,
        Authentication auth
    ) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        if (!quotaService.isPremium(user)) return ResponseEntity.status(402).build();
        List<DailyChallenge> challenges = challengeRepo.findByOrderByDateDescIdDesc(
            PageRequest.of(0, Math.min(limit, 100))
        );
        String playerId = "u" + user.getId();
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (DailyChallenge c : challenges) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("date", c.getDate().toString());
            m.put("scope", c.getScopeKey());
            m.put("genre", c.getGenreKey());
            m.put("questionCount", c.getArticleTitlesCsv().split("\t").length);
            m.put("played", service.hasPlayed(c.getId(), playerId));
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }
}
