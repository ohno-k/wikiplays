package com.wikiplays.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikiplays.dto.ArticleData;
import com.wikiplays.entity.CachedArticle;
import com.wikiplays.repository.CachedArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 検証済み記事を DB にキャッシュし、ゲーム出題に提供するサービス。
 *
 * - 出題要求 (`getRandom`) は DB から優先、なければ Wikipedia から取得して DB に追加
 * - 定期ジョブで各 (scope, genre) のキャッシュを補充
 * - 常識レベル (知名度 tier 1〜2) の記事はランダム取得ではほとんど当たらないので、
 *   「被リンク数の多い記事」を別ルートで取りに行って補充する (`fetchAndStoreFamous`)
 */
@Service
public class ArticlePoolService {

    private static final Logger log = LoggerFactory.getLogger(ArticlePoolService.class);

    /** 各 (scope, genre) ごとに保持したい記事数の目安。 */
    private static final long TARGET_PER_BUCKET = 150;
    /** 各 (scope, genre) ごとに保持したい常識レベル (tier 1〜2) の記事数の目安。 */
    static final long FAMOUS_TARGET_PER_BUCKET = 30;
    /** 起動時の即時補充で 1 バケットあたり追加する最大件数。 */
    private static final int INITIAL_REFILL_PER_BUCKET = 5;
    /** 起動時の即時補充で 1 バケットあたり確保する常識レベルの件数。 */
    private static final int INITIAL_FAMOUS_PER_BUCKET = 3;
    /** バックグラウンド補充で 1 バケットあたり追加する最大件数。 */
    private static final int SCHEDULED_REFILL_PER_BUCKET = 3;
    /** バックグラウンド補充で 1 バケットあたり追加する常識レベルの最大件数。 */
    private static final int SCHEDULED_FAMOUS_REFILL_PER_BUCKET = 3;
    /** 出題時に常識レベルの在庫が無かったとき、その場で Wikipedia から取りに行く最大件数。 */
    private static final int ON_DEMAND_FAMOUS_FETCH_MAX = 2;
    /** 被リンク数順の検索で 1 回に読む件数と、掘り進める深さの上限。 */
    private static final int FAMOUS_SEARCH_PAGE = 50;
    private static final int FAMOUS_SEARCH_MAX_OFFSET = 200;

    private final CachedArticleRepository repository;
    private final WikipediaService wikipediaService;
    private final ArticleFilter filter;
    private final ObjectMapper objectMapper = ArticleJson.MAPPER;

    /**
     * 被リンク数順検索の読み出し位置 (カテゴリごと)。
     * 上位が全部キャッシュ済みになったら次のページへ進み、深くなりすぎたら先頭に戻す。
     */
    private final Map<String, Integer> famousSearchOffset = new ConcurrentHashMap<>();

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

    /**
     * 知名度 tier を指定してランダムに 1 件。
     * 該当 tier に記事が無ければ近い tier で代替し (常識レベルならその場で補充も試み)、
     * それでも無ければ tier 無視で取得する。
     *
     * @param fameTier 1 (常識レベル) 〜 5 (超マニアック)。null なら指定なし
     */
    @Transactional
    public Optional<ArticleData> getRandom(String scope, String genre, Integer fameTier) {
        Integer tier = FameScorer.normalizeTier(fameTier);
        if (tier != null) {
            List<ArticleData> hit = pickByTier(scope, genre, tier, 1);
            if (!hit.isEmpty()) return Optional.of(hit.get(0));
            log.debug("no article in any fame tier for scope={} genre={}; falling back", scope, genre);
        }
        return getRandom(scope, genre);
    }

    /** タイトル指定で DB から記事を取得。 */
    @Transactional
    public Optional<ArticleData> findByTitle(String title) {
        return repository.findByTitle(title).flatMap(this::deserialize);
    }

    /** デイリーチャレンジ等で複数件取りたい場合に使う。 */
    @Transactional
    public List<ArticleData> getRandomSample(String scope, String genre, int count) {
        String normScope = normalize(scope);
        String normGenre = normalize(genre);
        List<CachedArticle> entries = repository.findRandomSample(normScope, normGenre, count);
        List<ArticleData> result = new ArrayList<>();
        for (CachedArticle c : entries) {
            deserialize(c).ifPresent(result::add);
        }
        return result;
    }

    /** 指定 tier ちょうどの記事を count 件まで (在庫が無ければ少なく返る)。 */
    @Transactional
    public List<ArticleData> getRandomSample(String scope, String genre, int fameTier, int count) {
        List<CachedArticle> entries = repository.findRandomSampleByFameRange(
            normalize(scope), normalize(genre),
            FameScorer.minScore(fameTier), FameScorer.maxScoreExclusive(fameTier), count);
        List<ArticleData> result = new ArrayList<>();
        for (CachedArticle c : entries) {
            c.setLastUsedAt(Instant.now());
            deserialize(c).ifPresent(result::add);
        }
        return result;
    }

    /**
     * 知名度 tier を優先して count 件選ぶ。
     *  1. 指定 tier の在庫から
     *  2. 常識レベル (tier 1〜2) の要求で在庫が足りなければ、その場で数件だけ Wikipedia から補充
     *  3. それでも足りなければ近い tier から順に補う (tier 1 の要求に tier 5 が混ざるのは最後の手段)
     * 全 tier 合わせても足りなければ少なく返る (tier 無視の補充は呼び出し側で行う)。
     */
    @Transactional
    public List<ArticleData> pickByTier(String scope, String genre, int fameTier, int count) {
        List<ArticleData> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addAll(out, seen, getRandomSample(scope, genre, fameTier, count), count);

        if (out.size() < count && fameTier <= FameScorer.FAMOUS_TIER_MAX) {
            int want = Math.min(ON_DEMAND_FAMOUS_FETCH_MAX, count - out.size());
            List<ArticleData> fetched = fetchAndStoreFamous(scope, genre, want);
            for (ArticleData a : fetched) {
                if (FameScorer.tierOf(FameScorer.score(a)) <= FameScorer.FAMOUS_TIER_MAX) {
                    addAll(out, seen, List.of(a), count);
                }
            }
            if (!fetched.isEmpty()) {
                log.info("on-demand famous fetch for scope={} genre={} tier={}: fetched {}",
                    scope, genre, fameTier, fetched.size());
            }
        }

        for (int t : FameScorer.fallbackOrder(fameTier)) {
            if (out.size() >= count) break;
            if (t == fameTier) continue;
            addAll(out, seen, getRandomSample(scope, genre, t, count - out.size()), count);
        }
        return out;
    }

    private static void addAll(List<ArticleData> out, Set<String> seen, List<ArticleData> src, int max) {
        for (ArticleData a : src) {
            if (out.size() >= max) return;
            if (seen.add(a.title())) out.add(a);
        }
    }

    /** (scope, genre) にある常識レベル (tier 1〜2) の記事数。 */
    @Transactional(readOnly = true)
    public long countFamous(String scope, String genre) {
        return repository.countByFameScoreAtLeast(
            normalize(scope), normalize(genre), FameScorer.minScore(FameScorer.FAMOUS_TIER_MAX));
    }

    /**
     * 知名度スコア未計算の記事に FameScorer の値を書き込む。
     * fame_score 列を追加した / 単位を変えたマイグレーションの後に一度だけ走る (起動時に呼ぶ)。
     *
     * @return 更新した件数
     */
    @Transactional
    public int backfillFameScores() {
        int updated = 0;
        while (true) {
            List<CachedArticle> batch = repository.findByFameScoreIsNull(PageRequest.of(0, 200));
            if (batch.isEmpty()) break;
            for (CachedArticle c : batch) {
                // 壊れた JSON は 0 点にしてループを終わらせる (tier 5 相当に落ちるだけ)
                c.setFameScore(deserialize(c).map(FameScorer::score).orElse(0.0));
            }
            repository.saveAll(batch);
            repository.flush();
            updated += batch.size();
        }
        if (updated > 0) log.info("fame score backfill: updated {} articles", updated);
        return updated;
    }

    /**
     * 閲覧数未取得の旧キャッシュに Wikipedia の閲覧数を書き込み、知名度スコアを実測値で再計算する。
     * 50 件ずつまとめて問い合わせるので数千件でも数分で終わる。
     * Wikipedia に到達できないときは途中で打ち切る (次の起動で続きから再開できる)。
     *
     * @return 閲覧数を書き込んだ件数
     */
    @Transactional
    public int backfillPageViews() {
        int updated = 0;
        long lastId = 0;
        while (true) {
            List<CachedArticle> batch = repository.findWithoutPageViewsAfterId(lastId, 50);
            if (batch.isEmpty()) break;
            lastId = batch.get(batch.size() - 1).getId();
            List<String> titles = new ArrayList<>();
            for (CachedArticle c : batch) titles.add(c.getTitle());
            Map<String, Integer> views;
            try {
                views = wikipediaService.fetchDailyPageViews(titles);
            } catch (Exception e) {
                log.warn("page view backfill aborted after {} articles: {}", updated, e.getMessage());
                break;
            }
            for (CachedArticle c : batch) {
                Integer v = views.get(c.getTitle());
                if (v == null) continue;
                Optional<ArticleData> data = deserialize(c);
                if (data.isEmpty()) continue;
                ArticleData withViews = data.get().withRecentPageViews(v);
                try {
                    c.setDataJson(objectMapper.writeValueAsString(withViews));
                } catch (JsonProcessingException e) {
                    continue;
                }
                c.setFameScore(FameScorer.score(withViews));
                updated++;
            }
            repository.saveAll(batch);
            repository.flush();
        }
        if (updated > 0) log.info("page view backfill: updated {} articles", updated);
        return updated;
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

    /**
     * 常識レベルの記事を狙って Wikipedia から取得し、DB に格納する。
     * ジャンルのカテゴリ木の中で「被リンク数の多い記事」(= 他の記事から最も言及される主題) を
     * 上位から順に読み、未キャッシュかつフィルタ通過のものを保存する。
     * 総合 (scope/genre なし) の場合はジャンルを 1 つ無作為に選び、そのバケットとして保存する
     * (総合の出題はプール全体から引くので、どのバケットに入っていても出題対象になる)。
     *
     * 保存した記事は tier 1〜2 とは限らない (被リンク数と閲覧数は一致しない)。
     * 実際の tier は保存時に計算した fame_score で決まる。
     *
     * @return 保存した記事 (最大 maxAdd 件)
     */
    public List<ArticleData> fetchAndStoreFamous(String scope, String genre, int maxAdd) {
        List<ArticleData> added = new ArrayList<>();
        if (maxAdd <= 0) return added;
        String normScope = normalize(scope);
        String normGenre = normalize(genre);
        List<String> categories = GenreCatalog.getCategories(normScope, normGenre);
        if (categories.isEmpty()) {
            List<GenreCatalog.Bucket> buckets = GenreCatalog.allBuckets();
            if (buckets.isEmpty()) return added;
            GenreCatalog.Bucket b = buckets.get(new java.util.Random().nextInt(buckets.size()));
            normScope = b.scope();
            normGenre = b.genre();
            categories = GenreCatalog.getCategories(normScope, normGenre);
        }
        List<String> shuffled = new ArrayList<>(categories);
        Collections.shuffle(shuffled);

        int attempts = 0;
        int maxAttempts = maxAdd * 4;
        for (String cat : shuffled) {
            if (added.size() >= maxAdd || attempts >= maxAttempts) break;
            String offsetKey = normScope + ":" + normGenre + ":" + cat;
            int offset = famousSearchOffset.getOrDefault(offsetKey, 0);
            List<String> candidates;
            try {
                candidates = wikipediaService.fetchMostLinkedInCategoryTree(cat, offset, FAMOUS_SEARCH_PAGE);
            } catch (Exception e) {
                log.debug("famous search failed for '{}': {}", cat, e.getMessage());
                continue;
            }
            boolean anyNew = false;
            for (String title : candidates) {
                if (added.size() >= maxAdd || attempts >= maxAttempts) break;
                if (repository.existsByTitle(title)) continue;
                anyNew = true;
                attempts++;
                try {
                    ArticleData data = wikipediaService.fetchArticleData(title);
                    if (repository.existsByTitle(data.title())) continue; // リダイレクト解決後の重複
                    if (!filter.isAllowed(data)) continue;
                    store(data, normScope, normGenre);
                    added.add(data);
                } catch (Exception e) {
                    log.debug("famous fetch failed for '{}': {}", title, e.getMessage());
                }
            }
            // このページの候補が全部キャッシュ済みなら次回は 1 ページ深く読む (深すぎたら先頭に戻す)
            if (!anyNew) {
                int next = offset + FAMOUS_SEARCH_PAGE;
                famousSearchOffset.put(offsetKey, next > FAMOUS_SEARCH_MAX_OFFSET ? 0 : next);
            }
        }
        return added;
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
            entity.setFameScore(FameScorer.score(data));
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
     * 30 分ごとに、まず常識レベルの在庫を、次に全体の在庫を各バケットに数件追加。
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
        int famousAdded = 0;
        for (String scope : scopes) {
            for (String genre : genres) {
                // null × null (総合) または同一ジャンルだけ
                if (scope == null && genre != null) continue;
                if (scope != null && genre == null) continue;
                if (countFamous(scope, genre) < FAMOUS_TARGET_PER_BUCKET) {
                    famousAdded += fetchAndStoreFamous(scope, genre, SCHEDULED_FAMOUS_REFILL_PER_BUCKET).size();
                }
                long current = repository.countByScopeAndGenre(scope, genre);
                if (current >= TARGET_PER_BUCKET) continue;
                int addLimit = SCHEDULED_REFILL_PER_BUCKET;
                for (int i = 0; i < addLimit; i++) {
                    if (fetchAndStore(scope, genre).isPresent()) totalAdded++;
                }
            }
        }
        if (totalAdded + famousAdded > 0) {
            log.info("article pool refill: added {} articles + {} famous (total = {})",
                totalAdded, famousAdded, repository.countAll());
        }
    }

    /** 起動時の初期投入。各バケットに少量だけ即補充する (常識レベルも数件確保する)。 */
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
                long famous = countFamous(scope, genre);
                if (famous < INITIAL_FAMOUS_PER_BUCKET) {
                    fetchAndStoreFamous(scope, genre, (int) (INITIAL_FAMOUS_PER_BUCKET - famous));
                }
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
