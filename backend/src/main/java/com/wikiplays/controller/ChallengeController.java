package com.wikiplays.controller;

import com.wikiplays.entity.Challenge;
import com.wikiplays.entity.Friendship;
import com.wikiplays.entity.User;
import com.wikiplays.repository.ChallengeRepository;
import com.wikiplays.repository.FriendshipRepository;
import com.wikiplays.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/challenges")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:4173"})
public class ChallengeController {

    private final ChallengeRepository challengeRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public ChallengeController(
        ChallengeRepository challengeRepository,
        FriendshipRepository friendshipRepository,
        UserRepository userRepository
    ) {
        this.challengeRepository = challengeRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    /**
     * 新しいチャレンジを作成 (自分のプレイ結果 + 同じ問題セットを友達に送る)。
     * body: { recipientUserId, mode, genre, scope, communityGenreId, articleTitles[], score }
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User me)) return ResponseEntity.status(401).build();
        Long recipientId = ((Number) body.get("recipientUserId")).longValue();
        if (recipientId.equals(me.getId())) return ResponseEntity.badRequest().body(Map.of("message", "自分自身には送れません"));

        // フレンド関係チェック (承認済み必須)
        boolean isFriend = friendshipRepository.findByRequesterIdAndAddresseeId(me.getId(), recipientId)
            .filter(f -> "ACCEPTED".equals(f.getStatus())).isPresent()
            || friendshipRepository.findByRequesterIdAndAddresseeId(recipientId, me.getId())
            .filter(f -> "ACCEPTED".equals(f.getStatus())).isPresent();
        if (!isFriend) return ResponseEntity.status(403).body(Map.of("message", "フレンドのみに送信できます"));

        @SuppressWarnings("unchecked")
        List<String> titles = (List<String>) body.get("articleTitles");
        if (titles == null || titles.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "問題セットが空です"));

        Challenge c = new Challenge();
        c.setCreatorId(me.getId());
        c.setRecipientId(recipientId);
        c.setMode(body.getOrDefault("mode", "a").toString());
        c.setGenre(body.get("genre") != null ? body.get("genre").toString() : null);
        c.setScope(body.get("scope") != null ? body.get("scope").toString() : null);
        if (body.get("communityGenreId") != null) c.setCommunityGenreId(((Number) body.get("communityGenreId")).longValue());
        c.setArticleTitlesCsv(String.join("\t", titles));
        c.setCreatorScore(((Number) body.getOrDefault("score", 0)).intValue());
        c.setStatus("PENDING");
        c.setCreatedAt(Instant.now());
        challengeRepository.save(c);
        return ResponseEntity.ok(Map.of("id", c.getId()));
    }

    /** 自分宛て (受信) のチャレンジ一覧。 */
    @GetMapping("/inbox")
    public ResponseEntity<?> inbox(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User me)) return ResponseEntity.status(401).build();
        List<Challenge> list = challengeRepository.findByRecipientIdAndStatusOrderByCreatedAtDesc(me.getId(), "PENDING");
        return ResponseEntity.ok(toBriefList(list, me.getId()));
    }

    /** 自分が関わる全チャレンジ (送受信含む)。 */
    @GetMapping
    public ResponseEntity<?> all(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User me)) return ResponseEntity.status(401).build();
        List<Challenge> list = challengeRepository.findByCreatorIdOrRecipientIdOrderByCreatedAtDesc(me.getId(), me.getId());
        return ResponseEntity.ok(toBriefList(list, me.getId()));
    }

    /** チャレンジ詳細 (記事タイトル一覧含む)。 */
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable("id") Long id, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User me)) return ResponseEntity.status(401).build();
        Optional<Challenge> opt = challengeRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Challenge c = opt.get();
        if (!c.getCreatorId().equals(me.getId()) && !c.getRecipientId().equals(me.getId())) {
            return ResponseEntity.status(403).build();
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", c.getId());
        result.put("mode", c.getMode());
        result.put("genre", c.getGenre());
        result.put("scope", c.getScope());
        result.put("communityGenreId", c.getCommunityGenreId());
        result.put("titles", java.util.Arrays.asList(c.getArticleTitlesCsv().split("\t")));
        result.put("creatorScore", c.getCreatorScore());
        result.put("recipientScore", c.getRecipientScore());
        result.put("status", c.getStatus());
        result.put("createdAt", c.getCreatedAt().toString());
        userRepository.findById(c.getCreatorId()).ifPresent(u -> result.put("creatorName", u.getDisplayName()));
        userRepository.findById(c.getRecipientId()).ifPresent(u -> result.put("recipientName", u.getDisplayName()));
        return ResponseEntity.ok(result);
    }

    /** 受信者がプレイ完了 → スコア送信。 */
    @PostMapping("/{id}/play")
    @Transactional
    public ResponseEntity<?> play(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User me)) return ResponseEntity.status(401).build();
        Optional<Challenge> opt = challengeRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Challenge c = opt.get();
        if (!c.getRecipientId().equals(me.getId())) return ResponseEntity.status(403).build();
        if (!"PENDING".equals(c.getStatus())) return ResponseEntity.badRequest().body(Map.of("message", "既にプレイ済みです"));
        c.setRecipientScore(body.getOrDefault("score", 0));
        c.setStatus("COMPLETED");
        c.setCompletedAt(Instant.now());
        return ResponseEntity.ok(Map.of(
            "creatorScore", c.getCreatorScore(),
            "recipientScore", c.getRecipientScore(),
            "winner", c.getCreatorScore() > c.getRecipientScore() ? "creator" :
                c.getCreatorScore() < c.getRecipientScore() ? "recipient" : "draw"
        ));
    }

    private List<Map<String, Object>> toBriefList(List<Challenge> challenges, Long meId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Challenge c : challenges) {
            Long otherId = c.getCreatorId().equals(meId) ? c.getRecipientId() : c.getCreatorId();
            String otherName = userRepository.findById(otherId).map(User::getDisplayName).orElse("?");
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", c.getId());
            m.put("status", c.getStatus());
            m.put("genre", c.getGenre());
            m.put("scope", c.getScope());
            m.put("isCreator", c.getCreatorId().equals(meId));
            m.put("otherName", otherName);
            m.put("creatorScore", c.getCreatorScore());
            m.put("recipientScore", c.getRecipientScore());
            m.put("createdAt", c.getCreatedAt().toString());
            out.add(m);
        }
        return out;
    }
}
