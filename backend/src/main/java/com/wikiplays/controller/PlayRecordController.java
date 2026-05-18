package com.wikiplays.controller;

import com.wikiplays.dto.PlayQuotaResponse;
import com.wikiplays.dto.PlayRecordSubmit;
import com.wikiplays.entity.PlayRecord;
import com.wikiplays.entity.User;
import com.wikiplays.repository.PlayRecordRepository;
import com.wikiplays.service.PlayQuotaService;
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

    public PlayRecordController(PlayRecordRepository repository, PlayQuotaService quotaService) {
        this.repository = repository;
        this.quotaService = quotaService;
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
        r.setScore(Math.max(0, Math.min(req.score(), 100_000)));
        r.setMaxScore(Math.max(1, Math.min(req.maxScore(), 100_000)));
        r.setDifficulty(req.difficulty());
        r.setPlayedAt(Instant.now());
        repository.save(r);
        return ResponseEntity.ok(Map.of("id", r.getId()));
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
