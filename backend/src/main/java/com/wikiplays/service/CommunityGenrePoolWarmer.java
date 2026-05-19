package com.wikiplays.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikiplays.dto.ArticleData;
import com.wikiplays.entity.CachedArticle;
import com.wikiplays.entity.CustomGenre;
import com.wikiplays.repository.CachedArticleRepository;
import com.wikiplays.repository.CustomGenreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * コミュニティジャンルのキャッシュプールを温めるサービス。
 *
 * - 手動 (POST /{id}/warm) と 定期実行 (@Scheduled) の両方からこのサービスを呼ぶ
 * - 1 ジャンルにつき Wikipedia カテゴリ → 記事 を巡回し、未キャッシュかつフィルタ通過のものを保存
 */
@Service
public class CommunityGenrePoolWarmer {

    private static final Logger log = LoggerFactory.getLogger(CommunityGenrePoolWarmer.class);

    /** 1 ジャンルあたり保持したい目標プール数 (これ未満のジャンルだけ定期補充の対象)。 */
    @Value("${wikiplays.community.target-pool-size:50}")
    private long targetPoolSize;

    /** 定期実行 1 サイクルで補充するジャンル数の上限。 */
    @Value("${wikiplays.community.max-genres-per-cycle:5}")
    private int maxGenresPerCycle;

    /** 定期実行で 1 ジャンルあたり追加する目標件数。 */
    @Value("${wikiplays.community.per-genre-target:8}")
    private int perGenreTarget;

    /** 定期実行 1 ジャンルあたりのデッドライン (ミリ秒)。 */
    @Value("${wikiplays.community.deadline-ms:30000}")
    private long scheduledDeadlineMs;

    /** 機能フラグ。トラブル時に false にして停止できる。 */
    @Value("${wikiplays.community.warmer-enabled:true}")
    private boolean enabled;

    private final CustomGenreRepository genreRepository;
    private final CachedArticleRepository cachedArticleRepository;
    private final WikipediaService wikipediaService;
    private final ArticleFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CommunityGenrePoolWarmer(
        CustomGenreRepository genreRepository,
        CachedArticleRepository cachedArticleRepository,
        WikipediaService wikipediaService,
        ArticleFilter filter
    ) {
        this.genreRepository = genreRepository;
        this.cachedArticleRepository = cachedArticleRepository;
        this.wikipediaService = wikipediaService;
        this.filter = filter;
    }

    public record WarmResult(int newlyCached, int attempted, int filterRejects, int fetchErrors) {}

    /**
     * 指定コミュニティジャンルのキャッシュを Wikipedia から温める。
     * @param genre 対象ジャンル
     * @param targetCount 新規追加したい上限件数
     * @param deadlineMs このメソッドの最大処理時間 (ミリ秒)
     */
    /**
     * 非ブロッキングでプールを温める (リクエスト応答中に呼ぶ用)。
     * 同一ジャンルに対する重複起動は防がず、内部の existsByTitle と deadline で自然に収束する。
     */
    @Async
    public void warmGenreAsync(CustomGenre genre) {
        if (!enabled || genre == null) return;
        try {
            WarmResult r = warmGenre(genre, perGenreTarget, scheduledDeadlineMs);
            if (r.newlyCached() > 0) {
                log.info("async warm done id={} name='{}' newly={}", genre.getId(), genre.getName(), r.newlyCached());
            }
        } catch (Exception e) {
            log.warn("async warm failed id={}: {}", genre.getId(), e.getMessage());
        }
    }

    public WarmResult warmGenre(CustomGenre genre, int targetCount, long deadlineMs) {
        int newlyCached = 0;
        int attempted = 0;
        int filterRejects = 0;
        int fetchErrors = 0;

        List<String> categories = new ArrayList<>(Arrays.asList(genre.getCategoriesCsv().split("\t")));
        Collections.shuffle(categories);
        final long deadline = System.currentTimeMillis() + deadlineMs;

        outer:
        for (String cat : categories) {
            if (newlyCached >= targetCount) break;
            if (System.currentTimeMillis() > deadline) break;
            List<String> members;
            try {
                members = wikipediaService.fetchCategoryMembers(cat, 100);
            } catch (Exception e) {
                log.warn("warm: category fetch failed '{}': {}", cat, e.getMessage());
                continue;
            }
            Collections.shuffle(members);
            for (String title : members) {
                if (newlyCached >= targetCount) break outer;
                if (System.currentTimeMillis() > deadline) break outer;
                if (cachedArticleRepository.existsByTitle(title)) continue;
                attempted++;
                try {
                    ArticleData data = wikipediaService.fetchArticleData(title);
                    if (filter.isAllowed(data)) {
                        storeToCache(data, genre.getId());
                        newlyCached++;
                    } else {
                        filterRejects++;
                    }
                } catch (Exception e) {
                    fetchErrors++;
                    log.debug("warm: article fetch failed '{}': {}", title, e.getMessage());
                }
            }
        }
        return new WarmResult(newlyCached, attempted, filterRejects, fetchErrors);
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

    /**
     * 定期補充: プールが targetPoolSize 未満のコミュニティジャンルを少しずつ補充する。
     * fixedDelay は前回終了から N ミリ秒。デフォルト 10 分。
     * 起動直後は混雑するので initialDelay でずらす。
     */
    @Scheduled(
        fixedDelayString = "${wikiplays.community.refill-interval:600000}",
        initialDelay = 180_000
    )
    public void scheduledRefill() {
        if (!enabled) return;
        List<CustomGenre> allGenres;
        try {
            allGenres = genreRepository.findAll();
        } catch (Exception e) {
            log.warn("community refill: failed to load genres: {}", e.getMessage());
            return;
        }
        if (allGenres.isEmpty()) return;

        // (id, currentPoolSize) を組んで、不足が大きい順に並べる
        record GenreNeed(CustomGenre genre, long currentSize, long shortfall) {}
        List<GenreNeed> candidates = new ArrayList<>();
        for (CustomGenre g : allGenres) {
            long size = cachedArticleRepository.countByCommunityGenreId(g.getId());
            if (size >= targetPoolSize) continue;
            candidates.add(new GenreNeed(g, size, targetPoolSize - size));
        }
        if (candidates.isEmpty()) return;
        candidates.sort(Comparator.comparingLong(GenreNeed::shortfall).reversed());

        int processed = 0;
        int totalAdded = 0;
        for (GenreNeed c : candidates) {
            if (processed >= maxGenresPerCycle) break;
            try {
                WarmResult r = warmGenre(c.genre(), perGenreTarget, scheduledDeadlineMs);
                totalAdded += r.newlyCached();
                log.info("community refill: id={} name='{}' size={} added={} attempted={} filterRejects={} fetchErrors={}",
                    c.genre().getId(), c.genre().getName(), c.currentSize(),
                    r.newlyCached(), r.attempted(), r.filterRejects(), r.fetchErrors());
            } catch (Exception e) {
                log.warn("community refill: failed for id={}: {}", c.genre().getId(), e.getMessage());
            }
            processed++;
        }
        if (totalAdded > 0) {
            log.info("community refill cycle done: processed={} totalAdded={}", processed, totalAdded);
        }
    }
}
