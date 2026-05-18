package com.wikiplays.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikiplays.dto.ArticleData;
import com.wikiplays.dto.CustomGenreCreate;
import com.wikiplays.dto.CustomGenreResponse;
import com.wikiplays.entity.CachedArticle;
import com.wikiplays.entity.CustomGenre;
import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
import com.wikiplays.repository.CachedArticleRepository;
import com.wikiplays.repository.CustomGenreRepository;
import com.wikiplays.repository.SubscriptionRepository;
import com.wikiplays.service.ArticleFilter;
import com.wikiplays.service.WikipediaService;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/community-genres")
public class CustomGenreController {

    private static final Logger log = LoggerFactory.getLogger(CustomGenreController.class);
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_CATEGORIES = 5;

    private final CustomGenreRepository repository;
    private final WikipediaService wikipediaService;
    private final ArticleFilter filter;
    private final SubscriptionRepository subscriptionRepository;
    private final CachedArticleRepository cachedArticleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomGenreController(
        CustomGenreRepository repository,
        WikipediaService wikipediaService,
        ArticleFilter filter,
        SubscriptionRepository subscriptionRepository,
        CachedArticleRepository cachedArticleRepository
    ) {
        this.repository = repository;
        this.wikipediaService = wikipediaService;
        this.filter = filter;
        this.subscriptionRepository = subscriptionRepository;
        this.cachedArticleRepository = cachedArticleRepository;
    }

    /** 人気順の一覧。 */
    @GetMapping
    public ResponseEntity<List<CustomGenreResponse>> list(
        @RequestParam(value = "playerId", required = false) String playerId,
        @RequestParam(value = "limit", defaultValue = "30") int limit
    ) {
        List<CustomGenre> entries = repository.findByOrderByPlayCountDescCreatedAtDesc(
            PageRequest.of(0, Math.min(limit, 100))
        );
        return ResponseEntity.ok(entries.stream().map(c -> toResponse(c, playerId)).toList());
    }

    /** 作成 (Premium ユーザーのみ)。 */
    @PostMapping
    @Transactional
    public ResponseEntity<CustomGenreResponse> create(@RequestBody CustomGenreCreate req, Authentication auth) {
        // Premium チェック
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        if (sub == null || !sub.isPremiumActive()) {
            return ResponseEntity.status(402).build(); // Payment Required
        }

        if (req.name() == null || req.name().isBlank()) return ResponseEntity.badRequest().build();
        if (req.creatorId() == null || req.creatorId().isBlank()) return ResponseEntity.badRequest().build();
        if (req.categories() == null || req.categories().isEmpty()) return ResponseEntity.badRequest().build();

        // カテゴリの倫理チェック: 各カテゴリ名に危険語を含まないか
        for (String cat : req.categories()) {
            if (cat == null || cat.isBlank()) continue;
            String lower = cat;
            if (lower.contains("殺人") || lower.contains("事件") || lower.contains("テロ")
                || lower.contains("自殺") || lower.contains("災害") || lower.contains("戦争")
                || lower.contains("虐殺") || lower.contains("ポルノ") || lower.contains("性風俗")) {
                return ResponseEntity.status(403).build();
            }
        }

        CustomGenre entity = new CustomGenre();
        entity.setName(truncate(req.name(), MAX_NAME_LENGTH));
        entity.setEmoji(req.emoji() == null || req.emoji().isBlank() ? "🏷️" : truncate(req.emoji(), 10));
        List<String> cats = req.categories().stream()
            .filter(s -> s != null && !s.isBlank())
            .limit(MAX_CATEGORIES)
            .toList();
        entity.setCategoriesCsv(String.join("\t", cats));
        entity.setCreatorId(req.creatorId());
        entity.setCreatorName(truncate(
            req.creatorName() == null || req.creatorName().isBlank() ? "名無し" : req.creatorName(),
            32
        ));
        entity.setCreatedAt(Instant.now());
        entity.setPlayCount(0);
        repository.save(entity);
        return ResponseEntity.ok(toResponse(entity, req.creatorId()));
    }

    /** 削除 (作成者のみ。Wikiplays 公式ジャンルは削除不可)。 */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(
        @PathVariable("id") Long id,
        @RequestParam("playerId") String playerId
    ) {
        Optional<CustomGenre> opt = repository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        CustomGenre target = opt.get();
        if (com.wikiplays.service.CommunityGenreSeeder.OFFICIAL_CREATOR_ID.equals(target.getCreatorId())) {
            return ResponseEntity.status(403).build();
        }
        if (!target.getCreatorId().equals(playerId)) return ResponseEntity.status(403).build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 指定コミュニティジャンルでランダムな記事を取得 (出題用)。
     * プレミアム会員限定。未ログインは 401、フリーは 402 を返す。
     *
     * 1. まず DB の CachedArticle に同コミュニティジャンルの記事がキャッシュされていれば、そこから返す
     * 2. キャッシュがなければ Wikipedia から少数取得→フィルタ通過した記事を DB にキャッシュ→返す
     *
     * Wikipedia 直叩きは API 遅延でリクエスト全体が長引くため、フォールバックは
     * 1 カテゴリ×最大 8 記事に絞る (10〜15 秒以内に必ず返ることを目標)。
     */
    @GetMapping("/{id}/random")
    @Transactional
    public ResponseEntity<ArticleData> random(@PathVariable("id") Long id, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        if (sub == null || !sub.isPremiumActive()) {
            return ResponseEntity.status(402).build();
        }
        Optional<CustomGenre> opt = repository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        CustomGenre genre = opt.get();

        // (1) キャッシュから引く
        Optional<CachedArticle> cached = cachedArticleRepository.findRandomByCommunityGenreId(id);
        if (cached.isPresent()) {
            CachedArticle c = cached.get();
            c.setLastUsedAt(Instant.now());
            try {
                ArticleData data = objectMapper.readValue(c.getDataJson(), ArticleData.class);
                genre.setPlayCount(genre.getPlayCount() + 1);
                return ResponseEntity.ok(data);
            } catch (JsonProcessingException e) {
                log.warn("failed to deserialize cached article id={}: {}", c.getId(), e.getMessage());
                // フォールスルー、Wikipedia から取りに行く
            }
        }

        // (2) Wikipedia フォールバック (15 秒以内に諦める)
        List<String> categories = new ArrayList<>(Arrays.asList(genre.getCategoriesCsv().split("\t")));
        Collections.shuffle(categories);
        ArticleData firstFound = null;
        int totalFetched = 0;
        final int MAX_FETCHES = 20;
        final long DEADLINE_MS = System.currentTimeMillis() + 15_000;

        for (String cat : categories) {
            if (firstFound != null) break;
            if (System.currentTimeMillis() > DEADLINE_MS) break;
            try {
                List<String> members = wikipediaService.fetchCategoryMembers(cat, 30);
                if (members.isEmpty()) continue;
                Collections.shuffle(members);
                for (String title : members) {
                    if (totalFetched >= MAX_FETCHES) break;
                    if (System.currentTimeMillis() > DEADLINE_MS) break;
                    if (cachedArticleRepository.existsByTitle(title)) continue;
                    totalFetched++;
                    try {
                        ArticleData data = wikipediaService.fetchArticleData(title);
                        if (filter.isAllowed(data)) {
                            // DB にキャッシュ (次回以降は即時返却できる)
                            storeToCache(data, id);
                            if (firstFound == null) firstFound = data;
                            break;
                        }
                    } catch (Exception e) {
                        log.debug("fetch fail '{}': {}", title, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("category fetch fail '{}': {}", cat, e.getMessage());
            }
        }
        log.info("community random for id={}: fetched={}, found={}", id, totalFetched, firstFound != null);

        if (firstFound != null) {
            genre.setPlayCount(genre.getPlayCount() + 1);
            return ResponseEntity.ok(firstFound);
        }
        return ResponseEntity.status(503).build();
    }

    private void storeToCache(ArticleData data, Long communityGenreId) {
        try {
            if (cachedArticleRepository.existsByTitle(data.title())) return;
            CachedArticle entity = new CachedArticle();
            entity.setTitle(data.title());
            entity.setCommunityGenreId(communityGenreId);
            entity.setDataJson(objectMapper.writeValueAsString(data));
            entity.setExtractedYear(data.extractedYear());
            entity.setExtractedYearKind(data.extractedYearKind());
            entity.setCreatedAt(Instant.now());
            entity.setLastUsedAt(Instant.now());
            cachedArticleRepository.save(entity);
        } catch (JsonProcessingException e) {
            log.warn("failed to serialize article '{}': {}", data.title(), e.getMessage());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.debug("concurrent save skipped for '{}'", data.title());
        }
    }

    private CustomGenreResponse toResponse(CustomGenre c, String playerId) {
        List<String> cats = Arrays.asList(c.getCategoriesCsv().split("\t"));
        boolean mine = playerId != null && playerId.equals(c.getCreatorId());
        return new CustomGenreResponse(
            c.getId(), c.getName(), c.getEmoji(), cats,
            c.getCreatorName(), c.getCreatedAt(), c.getPlayCount(), mine
        );
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
