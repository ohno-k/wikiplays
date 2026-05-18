package com.wikiplays.controller;

import com.wikiplays.dto.DailyChallengeResponse;
import com.wikiplays.dto.DailyLeaderboardEntry;
import com.wikiplays.dto.DailyScoreSubmit;
import com.wikiplays.entity.DailyChallenge;
import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
import com.wikiplays.repository.DailyChallengeRepository;
import com.wikiplays.repository.SubscriptionRepository;
import com.wikiplays.service.DailyChallengeService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/daily")
public class DailyController {

    private final DailyChallengeService service;
    private final DailyChallengeRepository challengeRepo;
    private final SubscriptionRepository subscriptionRepo;

    public DailyController(
        DailyChallengeService service,
        DailyChallengeRepository challengeRepo,
        SubscriptionRepository subscriptionRepo
    ) {
        this.service = service;
        this.challengeRepo = challengeRepo;
        this.subscriptionRepo = subscriptionRepo;
    }

    /** 今日のチャレンジを取得 (なければ生成)。登録ユーザー限定。 */
    @GetMapping("/today")
    public ResponseEntity<DailyChallengeResponse> today(
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "genre", required = false) String genre,
        Authentication auth
    ) {
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            return ResponseEntity.status(401).build();
        }
        Optional<DailyChallengeResponse> resp = service.getToday(scope, genre);
        return resp.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(503).build());
    }

    /** スコア提出。登録ユーザー限定 (1 日 1 回、user.id ベースで重複防止)。 */
    @PostMapping("/submit")
    public ResponseEntity<Void> submit(@RequestBody DailyScoreSubmit req, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        // クライアントの playerId / displayName は使わず、ログインユーザー情報で上書きする
        DailyScoreSubmit normalized = new DailyScoreSubmit(
            req.dailyChallengeId(),
            "u" + user.getId(),
            user.getDisplayName(),
            req.score()
        );
        boolean ok = service.submit(normalized);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    /** ランキング取得。 */
    @GetMapping("/leaderboard/{challengeId}")
    public ResponseEntity<List<DailyLeaderboardEntry>> leaderboard(
        @PathVariable("challengeId") Long challengeId,
        @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(service.leaderboard(challengeId, limit));
    }

    /** 過去デイリーチャレンジ一覧 (Premium 限定)。 */
    @GetMapping("/archive")
    public ResponseEntity<?> archive(
        @RequestParam(value = "limit", defaultValue = "30") int limit,
        Authentication auth
    ) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        Subscription sub = subscriptionRepo.findByUserId(user.getId()).orElse(null);
        if (sub == null || !sub.isPremiumActive()) {
            return ResponseEntity.status(402).build();
        }
        List<DailyChallenge> challenges = challengeRepo.findByOrderByDateDescIdDesc(
            PageRequest.of(0, Math.min(limit, 100))
        );
        // 詳細を返す (記事タイトル一覧のみ。プレイ時に full データ取得)
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (DailyChallenge c : challenges) {
            result.add(java.util.Map.of(
                "id", c.getId(),
                "date", c.getDate().toString(),
                "scope", c.getScopeKey().isEmpty() ? "" : c.getScopeKey(),
                "genre", c.getGenreKey().isEmpty() ? "" : c.getGenreKey(),
                "titles", java.util.List.of(c.getArticleTitlesCsv().split("\t"))
            ));
        }
        return ResponseEntity.ok(result);
    }
}
