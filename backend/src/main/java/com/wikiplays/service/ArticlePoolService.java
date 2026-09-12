package com.wikiplays.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikiplays.dto.ArticleData;
import com.wikiplays.entity.CachedArticle;
import com.wikiplays.repository.CachedArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 検証済み記事を DB にキャッシュし、ゲーム出題に提供するサービス。
 *
 * - 出題要求 (`getRandom`) は DB から優先、なければ Wikipedia から取得して DB に追加
 * - 定期ジョブで各 (scope, genre) のキャッシュを補充
 */
@Service
public class ArticlePoolService {

    private static final Logger log = LoggerFactory.getLogger(ArticlePoolService.class);

    /** 各 (scope, genre) ごとに保持したい記事数の目安。 */
    private static final long TARGET_PER_BUCKET = 80;
    /** 起動時の即時補充で 1 バケットあたり追加する最大件数。 */
    private static final int INITIAL_REFILL_PER_BUCKET = 5;
    /** バックグラウンド補充で 1 バケットあたり追加する最大件数。 */
    private static final int SCHEDULED_REFILL_PER_BUCKET = 3;

    private final CachedArticleRepository repository;
    private final WikipediaService wikipediaService;
    private final ArticleFilter filter;
    private final ObjectMapper objectMapper = ArticleJson.MAPPER;

    public ArticlePoolService(
        CachedArticleRepository repository,
        WikipediaService wikipediaService,
        ArticleFilter filter
    ) {
        this.repository = repository;
        this.wikipediaService = wikipediaService;
        this.filter = filter;
    }

    /** DB に十分な数があれば DB から、なければ Wikipedia から取得して DB にキャッシュした上で返す。 */
    @Transactional
    public Optional<ArticleData> getRandom(String scope, String genre) {
        String normScope = normalize(scope);
        String normGenre = normalize(genre);
        Optional<CachedArticle> cached = repository.findRandomByScopeAndGenre(normScope, normGenre);
        if (cached.isPresent()) {
            cached.get().setLastUsedAt(Instant.now());
            return cached.map(this::deserialize).filter(Optional::isPresent).map(Optional::get);
        }
        // キャッシュなし → Wikipedia から取得して 1 件 DB に追加
        return fetchAndStore(normScope, normGenre);
    }

    /** タイトル指定で DB から記事を取得。 */
    @Transactional
    public Optional<ArticleData> findByTitle(String title) {
        return repository.findByTitle(title).flatMap(this::deserialize);
    }

    /** デイリーチャレンジ等で複数件取りたい場合に使う。 */
    @Transactional
    public java.util.List<ArticleData> getRandomSample(String scope, String genre, int count) {
        String normScope = normalize(scope);
        String normGenre = normalize(genre);
        java.util.List<CachedArticle> entries = repository.findRandomSample(normScope, normGenre, count);
        java.util.List<ArticleData> result = new java.util.ArrayList<>();
        for (CachedArticle c : entries) {
            deserialize(c).ifPresent(result::add);
        }
        return result;
    }

    /**
     * 1 バケット分の記事を Wikipedia から取得して DB に格納する。
     * フィルタを通過して保存に成功した記事の ArticleData を返す。
     */
    private Optional<ArticleData> fetchAndStore(String scope, String genre) {
        for (int i = 0; i < 10; i++) {
            try {
                String title = (genre == null || scope == null)
                    ? wikipediaService.fetchRandomTitle()
                    : wikipediaService.fetchRandomTitleByGenre(scope, genre);
                if (repository.existsByTitle(title)) continue; // 既にキャッシュ済み
                ArticleData data = wikipediaService.fetchArticleData(title);
                if (!filter.isAllowed(data)) continue;
                store(data, scope, genre);
                return Optional.of(data);
            } catch (Exception e) {
                log.debug("fetch failed: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    private void store(ArticleData data, String scope, String genre) {
        try {
            // 並行アクセスで既に保存されていないか再チェック
            if (repository.existsByTitle(data.title())) return;
            CachedArticle entity = new CachedArticle();
            entity.setTitle(data.title());
            entity.setScope(scope);
            entity.setGenre(genre);
            entity.setDataJson(objectMapper.writeValueAsString(data));
            entity.setExtractedYear(data.extractedYear());
            entity.setExtractedYearKind(data.extractedYearKind());
            entity.setCreatedAt(Instant.now());
            entity.setLastUsedAt(Instant.now());
            repository.save(entity);
        } catch (JsonProcessingException e) {
            log.warn("failed to serialize article '{}': {}", data.title(), e.getMessage());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 並行保存による UK 違反は無視 (別スレッドが先に同じ記事を保存しただけ)
            log.debug("concurrent save skipped for '{}'", data.title());
        }
    }

    private Optional<ArticleData> deserialize(CachedArticle entity) {
        try {
            return Optional.of(objectMapper.readValue(entity.getDataJson(), ArticleData.class));
        } catch (JsonProcessingException e) {
            log.warn("failed to deserialize cached article id={}: {}", entity.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private String normalize(String s) {
        if (s == null || s.isBlank() || "random".equals(s)) return null;
        return s;
    }

    /**
     * 定期的に各 (scope, genre) バケットを補充する。
     * 30 分ごとに各バケットに数件追加。
     */
    @Scheduled(fixedDelayString = "${wikiplays.pool.refill-interval:1800000}", initialDelay = 60_000)
    @Transactional
    public void scheduledRefill() {
        // 主要なバケットを順に補充
        String[] scopes = {"jp", "world", null}; // null は総合
        String[] genres = {
            null, "rail", "history", "geography", "science", "biology", "plant",
            "paleontology", "astronomy", "art", "literature", "music", "movie",
            "anime", "sports", "mythology", "architecture", "vehicle", "language",
            "computer", "food"
        };
        int totalAdded = 0;
        for (String scope : scopes) {
            for (String genre : genres) {
                // null × null (総合) または同一ジャンルだけ
                if (scope == null && genre != null) continue;
                if (scope != null && genre == null) continue;
                long current = repository.countByScopeAndGenre(scope, genre);
                if (current >= TARGET_PER_BUCKET) continue;
                int addLimit = SCHEDULED_REFILL_PER_BUCKET;
                for (int i = 0; i < addLimit; i++) {
                    if (fetchAndStore(scope, genre).isPresent()) totalAdded++;
                }
            }
        }
        if (totalAdded > 0) {
            log.info("article pool refill: added {} articles (total = {})", totalAdded, repository.countAll());
        }
    }

    /** 起動時の初期投入。各バケットに少量だけ即補充する。 */
    public void initialPopulate() {
        String[] scopes = {"jp", "world", null};
        String[] genres = {
            null, "rail", "history", "geography", "science", "biology",
            "art", "literature", "music", "movie", "anime", "sports", "food"
        };
        for (String scope : scopes) {
            for (String genre : genres) {
                if (scope == null && genre != null) continue;
                if (scope != null && genre == null) continue;
                long current = repository.countByScopeAndGenre(scope, genre);
                if (current >= INITIAL_REFILL_PER_BUCKET) continue;
                int addLimit = (int) (INITIAL_REFILL_PER_BUCKET - current);
                for (int i = 0; i < addLimit; i++) {
                    fetchAndStore(scope, genre);
                }
            }
        }
    }
}
