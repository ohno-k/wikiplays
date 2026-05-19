package com.wikiplays.controller;

import com.wikiplays.dto.PlayQuotaResponse;
import com.wikiplays.dto.PlayRecordSubmit;
import com.wikiplays.entity.PlayRecord;
import com.wikiplays.entity.User;
import com.wikiplays.repository.PlayRecordRepository;
import com.wikiplays.service.PlayQuotaService;
import com.wikiplays.service.XpService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/play")
public class PlayRecordController {

    private final PlayRecordRepository repository;
    private final PlayQuotaService quotaService;
    private final XpService xpService;

    public PlayRecordController(PlayRecordRepository repository, PlayQuotaService quotaService, XpService xpService) {
        this.repository = repository;
        this.quotaService = quotaService;
        this.xpService = xpService;
    }

    /** プレイ結果を記録 (匿名 OK、ログインなら user_id 紐付け)。 */
    @PostMapping("/record")
    public ResponseEntity<?> record(@RequestBody PlayRecordSubmit req, Authentication auth) {
        User user = (auth != null && auth.getPrincipal() instanceof User u) ? u : null;
        PlayRecord r = new PlayRecord();
        r.setUserId(user != null ? user.getId() : null);
        r.setPlayerId(user == null ? req.playerId() : null);
        r.setMode(req.mode());
        r.setGenre(req.genre());
        r.setScope(req.scope());
        r.setCommunityGenreId(req.communityGenreId());
        int clampedScore = Math.max(0, Math.min(req.score(), 100_000));
        r.setScore(clampedScore);
        r.setMaxScore(Math.max(1, Math.min(req.maxScore(), 100_000)));
        r.setDifficulty(req.difficulty());
        r.setPlayedAt(Instant.now());
        repository.save(r);

        // ログインユーザーのみ XP を付与
        XpService.AwardResult xp = (user != null) ? xpService.award(user, clampedScore) : null;
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", r.getId());
        if (xp != null) {
            body.put("xpGained", xp.gained());
            body.put("xpCapped", xp.capped());
            body.put("leveledUp", xp.leveledUp());
            body.put("xp", xp.info().xp());
            body.put("level", xp.info().level());
            body.put("xpIntoLevel", xp.info().xpIntoLevel());
            body.put("xpForNextLevel", xp.info().xpForNextLevel());
            body.put("dailyRemaining", xp.info().dailyRemaining());
        }
        return ResponseEntity.ok(body);
    }

    /** 今日のプレイ可能残数を取得。 */
    @GetMapping("/quota")
    public ResponseEntity<PlayQuotaResponse> quota(
        Authentication auth,
        @RequestParam(value = "playerId", required = false) String playerId
    ) {
        User user = (auth != null && auth.getPrincipal() instanceof User u) ? u : null;
        return ResponseEntity.ok(quotaService.check(user, playerId));
    }
}
