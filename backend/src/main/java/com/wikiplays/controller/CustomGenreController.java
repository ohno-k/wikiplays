package com.wikiplays.controller;

import com.wikiplays.dto.ArticleData;
import com.wikiplays.dto.CustomGenreCreate;
import com.wikiplays.dto.CustomGenreResponse;
import com.wikiplays.entity.CustomGenre;
import com.wikiplays.entity.Subscription;
import com.wikiplays.entity.User;
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
import org.springframework.web.bind.annotation.CrossOrigin;
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
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:4173"})
public class CustomGenreController {

    private static final Logger log = LoggerFactory.getLogger(CustomGenreController.class);
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_CATEGORIES = 5;

    private final CustomGenreRepository repository;
    private final WikipediaService wikipediaService;
    private final ArticleFilter filter;
    private final SubscriptionRepository subscriptionRepository;

    public CustomGenreController(
        CustomGenreRepository repository,
        WikipediaService wikipediaService,
        ArticleFilter filter,
        SubscriptionRepository subscriptionRepository
    ) {
        this.repository = repository;
        this.wikipediaService = wikipediaService;
        this.filter = filter;
        this.subscriptionRepository = subscriptionRepository;
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

    /** 削除 (作成者のみ)。 */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(
        @PathVariable("id") Long id,
        @RequestParam("playerId") String playerId
    ) {
        Optional<CustomGenre> opt = repository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!opt.get().getCreatorId().equals(playerId)) return ResponseEntity.status(403).build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 指定コミュニティジャンルでランダムな記事を取得 (出題用)。
     * カテゴリ群を順次試して、フィルタを通過した記事を返す。
     */
    @GetMapping("/{id}/random")
    @Transactional
    public ResponseEntity<ArticleData> random(@PathVariable("id") Long id) {
        Optional<CustomGenre> opt = repository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        CustomGenre genre = opt.get();
        List<String> categories = new ArrayList<>(Arrays.asList(genre.getCategoriesCsv().split("\t")));
        Collections.shuffle(categories);

        for (int attempt = 0; attempt < 10; attempt++) {
            for (String cat : categories) {
                try {
                    List<String> members = wikipediaService.fetchCategoryMembers(cat, 30);
                    if (members.isEmpty()) continue;
                    Collections.shuffle(members);
                    for (String title : members) {
                        try {
                            ArticleData data = wikipediaService.fetchArticleData(title);
                            if (filter.isAllowed(data)) {
                                genre.setPlayCount(genre.getPlayCount() + 1);
                                return ResponseEntity.ok(data);
                            }
                        } catch (Exception e) {
                            log.debug("fetch fail '{}': {}", title, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("category fetch fail '{}': {}", cat, e.getMessage());
                }
            }
        }
        return ResponseEntity.status(503).build();
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
