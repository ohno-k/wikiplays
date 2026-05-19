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
import com.wikiplays.service.CommunityGenrePoolWarmer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final CommunityGenrePoolWarmer poolWarmer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomGenreController(
        CustomGenreRepository repository,
        WikipediaService wikipediaService,
        ArticleFilter filter,
        SubscriptionRepository subscriptionRepository,
        CachedArticleRepository cachedArticleRepository,
        CommunityGenrePoolWarmer poolWarmer
    ) {
        this.repository = repository;
        this.wikipediaService = wikipediaService;
        this.filter = filter;
        this.subscriptionRepository = subscriptionRepository;
        this.cachedArticleRepository = cachedArticleRepository;
        this.poolWarmer = poolWarmer;
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
    public ResponseEntity<ArticleData> random(
        @PathVariable("id") Long id,
        @RequestParam(name = "exclude", required = false) List<String> excludeTitles,
        Authentication auth
    ) {
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

        // (0) セッションでプールを使い切っているなら、Wikipedia の 20 秒待ちをスキップして
        //     即座に重複から返す。同時にバックグラウンドでプールを補充する。
        //     プールが小さい (数件) コミュニティジャンルで Q2 以降がブラウザタイムアウトする問題への対策。
        long poolSize = cachedArticleRepository.countByCommunityGenreId(id);
        int excludeSize = excludeTitles == null ? 0 : excludeTitles.size();
        if (poolSize > 0 && excludeSize >= poolSize) {
            poolWarmer.warmGenreAsync(genre);
            Optional<CachedArticle> dup = cachedArticleRepository.findRandomByCommunityGenreId(id);
            if (dup.isPresent()) {
                CachedArticle c = dup.get();
                c.setLastUsedAt(Instant.now());
                try {
                    ArticleData data = objectMapper.readValue(c.getDataJson(), ArticleData.class);
                    log.debug("community random id={}: pool exhausted (size={}, excluded={}), returning duplicate",
                        id, poolSize, excludeSize);
                    return ResponseEntity.ok(data);
                } catch (JsonProcessingException e) {
                    log.warn("failed to deserialize cached article id={}: {}", c.getId(), e.getMessage());
                }
            }
        }

        // (1) キャッシュから引く (exclude 指定があれば除外)
        Optional<CachedArticle> cached = (excludeTitles != null && !excludeTitles.isEmpty())
            ? cachedArticleRepository.findRandomByCommunityGenreIdExcluding(id, excludeTitles)
            : cachedArticleRepository.findRandomByCommunityGenreId(id);
        if (cached.isPresent()) {
            CachedArticle c = cached.get();
            c.setLastUsedAt(Instant.now());
            try {
                ArticleData data = objectMapper.readValue(c.getDataJson(), ArticleData.class);
                // playCount は記事 1 件ごとには加算しない (POST /{id}/play でセッション単位に加算する)
                return ResponseEntity.ok(data);
            } catch (JsonProcessingException e) {
                log.warn("failed to deserialize cached article id={}: {}", c.getId(), e.getMessage());
                // フォールスルー、Wikipedia から取りに行く
            }
        }

        // (2) Wikipedia フォールバック (20 秒以内に諦める)
        //     キャッシュが空、または exclude で全弾打ち尽くした場合に走る
        List<String> categories = new ArrayList<>(Arrays.asList(genre.getCategoriesCsv().split("\t")));
        Collections.shuffle(categories);
        ArticleData firstFound = null;
        int totalFetched = 0;
        final int MAX_FETCHES = 30;
        final long DEADLINE_MS = System.currentTimeMillis() + 20_000;
        java.util.Set<String> excludeSet = excludeTitles == null
            ? java.util.Collections.emptySet()
            : new java.util.HashSet<>(excludeTitles);

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
                    if (excludeSet.contains(title)) continue;
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
        log.info("community random for id={}: fetched={}, found={}, excludeCount={}",
            id, totalFetched, firstFound != null, excludeSet.size());

        if (firstFound != null) {
            // playCount は記事 1 件ごとには加算しない (POST /{id}/play で別カウント)
            return ResponseEntity.ok(firstFound);
        }

        // (3) 最後の手段: exclude を無視してでもキャッシュから返す。
        //     プールが極端に小さいコミュニティジャンルで、Wikipedia 取得も間に合わなかったとき、
        //     503 を返すよりは同じ記事を出題する方が UX 上マシ。
        if (excludeTitles != null && !excludeTitles.isEmpty()) {
            Optional<CachedArticle> anyCached = cachedArticleRepository.findRandomByCommunityGenreId(id);
            if (anyCached.isPresent()) {
                poolWarmer.warmGenreAsync(genre);
                CachedArticle c = anyCached.get();
                c.setLastUsedAt(Instant.now());
                try {
                    ArticleData data = objectMapper.readValue(c.getDataJson(), ArticleData.class);
                    log.info("community random for id={}: falling back to duplicate (pool exhausted)", id);
                    return ResponseEntity.ok(data);
                } catch (JsonProcessingException e) {
                    log.warn("failed to deserialize last-resort cached article id={}: {}", c.getId(), e.getMessage());
                }
            }
        }

        // プールも Wikipedia も駄目だったので 503。次回プレイ時に向けて非同期で温める。
        poolWarmer.warmGenreAsync(genre);
        return ResponseEntity.status(503).build();
    }

    /**
     * 管理者用: コミュニティジャンルのキャッシュを Wikipedia から温める。
     * カテゴリ → メンバー記事 を巡回し、未キャッシュかつフィルタ通過のものを最大 n 件保存する。
     *
     * 呼び出し例:
     *   curl -X POST -H "Authorization: Bearer <ADMIN_JWT>" \
     *     "https://wikiplays.me/api/community-genres/22/warm?n=20"
     *
     * 1 回の呼び出しに最大 60 秒かかる場合がある。必要なら複数回叩いてプールを育てる。
     */
    @PostMapping("/{id}/warm")
    public ResponseEntity<Map<String, Object>> warmCache(
        @PathVariable("id") Long id,
        @RequestParam(name = "n", defaultValue = "10") int targetCount,
        Authentication auth
    ) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }
        if (!"ADMIN".equals(user.getRole())) {
            return ResponseEntity.status(403).build();
        }
        Optional<CustomGenre> opt = repository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        CustomGenre genre = opt.get();

        int n = Math.max(1, Math.min(targetCount, 30));
        CommunityGenrePoolWarmer.WarmResult r = poolWarmer.warmGenre(genre, n, 60_000);
        long totalCached = cachedArticleRepository.countByCommunityGenreId(id);
        log.info("warm community id={}: newly={}, attempted={}, filterRejects={}, fetchErrors={}, total={}",
            id, r.newlyCached(), r.attempted(), r.filterRejects(), r.fetchErrors(), totalCached);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("communityGenreId", id);
        result.put("genreName", genre.getName());
        result.put("targetCount", n);
        result.put("newlyCached", r.newlyCached());
        result.put("attempted", r.attempted());
        result.put("filterRejects", r.filterRejects());
        result.put("fetchErrors", r.fetchErrors());
        result.put("totalCached", totalCached);
        return ResponseEntity.ok(result);
    }

    /**
     * プレイ開始を記録する。フロントエンド側で 1 セッションごとに 1 回だけ呼ぶ。
     * /random は 1 問につき複数回呼ばれる (先読み・重複スキップ) ため、
     * プレイ回数として正確に集計するにはこちらで加算する必要がある。
     */
    @PostMapping("/{id}/play")
    @Transactional
    public ResponseEntity<Void> recordPlay(@PathVariable("id") Long id, Authentication auth) {
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
        genre.setPlayCount(genre.getPlayCount() + 1);

        // プレイ開始時に、プールが小さければバックグラウンドで補充をキック。
        // 5 問のセッションを快適に回すには最低 10 件程度ほしい。
        long poolSize = cachedArticleRepository.countByCommunityGenreId(id);
        if (poolSize < 10) {
            poolWarmer.warmGenreAsync(genre);
        }
        return ResponseEntity.noContent().build();
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
