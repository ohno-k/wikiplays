package com.wikiplays.controller;

import com.wikiplays.dto.ArticleData;
import com.wikiplays.entity.User;
import com.wikiplays.service.ArticlePoolService;
import com.wikiplays.service.PlayQuotaService;
import com.wikiplays.service.WikipediaService;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/article")
public class ArticleController {

    private static final Logger log = LoggerFactory.getLogger(ArticleController.class);

    private final WikipediaService wikipediaService;
    private final ArticlePoolService articlePool;
    private final PlayQuotaService quotaService;

    public ArticleController(
        WikipediaService wikipediaService,
        ArticlePoolService articlePool,
        PlayQuotaService quotaService
    ) {
        this.wikipediaService = wikipediaService;
        this.articlePool = articlePool;
        this.quotaService = quotaService;
    }

    /**
     * フィルタ通過する記事を 1 件返す (B〜E モード用)。
     * まず記事プール (DB) から、ヒットしなければ Wikipedia から取得して DB に追加。
     * fameTier (1 = 常識レベル 〜 5 = 超マニアック) を指定すると、その知名度帯の記事を優先する。
     * Free プランの 1 日上限に達している場合は 429。
     */
    @GetMapping("/random")
    public ResponseEntity<?> random(
        @RequestParam(value = "genre", required = false) String genre,
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "fameTier", required = false) Integer fameTier,
        @RequestParam(value = "playerId", required = false) String playerId,
        Authentication auth
    ) {
        User user = (auth != null && auth.getPrincipal() instanceof User u) ? u : null;
        if (!quotaService.canPlay(user, playerId)) {
            return ResponseEntity.status(429).body(java.util.Map.of("message", "今日のプレイ上限に達しました"));
        }
        Optional<ArticleData> data = articlePool.getRandom(scope, genre, fameTier);
        return data.<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(503).build());
    }

    /**
     * モード D 用: 正解記事のカテゴリから、ダミー選択肢候補を返す。
     */
    @GetMapping("/decoys")
    public ResponseEntity<List<String>> decoys(
        @RequestParam("categories") List<String> categories,
        @RequestParam("exclude") String exclude,
        @RequestParam(value = "count", defaultValue = "3") int count
    ) {
        List<String> specific = categories.stream()
            .filter(c -> !isBroadCategory(c))
            .toList();
        List<String> useCats = specific.isEmpty() ? categories : specific;

        Set<String> bag = new LinkedHashSet<>();
        for (String cat : useCats) {
            try {
                for (String t : wikipediaService.fetchCategoryMembers(cat, 30)) {
                    if (!t.equals(exclude)) bag.add(t);
                }
            } catch (Exception e) {
                log.warn("decoy fetch failed for category '{}': {}", cat, e.getMessage());
            }
            if (bag.size() >= count * 4) break;
        }
        List<String> shuffled = new ArrayList<>(bag);
        Collections.shuffle(shuffled);
        return ResponseEntity.ok(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    private boolean isBroadCategory(String cat) {
        if (cat == null || cat.isBlank()) return true;
        if (cat.matches(".*\\d+年.*")) return true;
        if (cat.matches(".*\\d+世紀.*")) return true;
        if (cat.matches(".*\\d+年代.*")) return true;
        if (cat.contains("世紀の")) return true;
        if (cat.contains("存命人物")) return true;
        if (cat.equals("故人")) return true;
        if (cat.equals("人物")) return true;
        if (cat.startsWith("各国の")) return true;
        if (cat.startsWith("各地域の")) return true;
        return false;
    }
}
