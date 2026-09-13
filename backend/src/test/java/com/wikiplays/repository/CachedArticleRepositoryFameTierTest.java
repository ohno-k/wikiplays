package com.wikiplays.repository;

import com.wikiplays.entity.CachedArticle;
import com.wikiplays.service.FameScorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 知名度 tier 抽出クエリ (絶対値の範囲指定) が H2 上で意図どおりに動くことの確認。
 * (アプリケーションクラスがサービスを autowire しているため JPA スライスでは起動できず、フルコンテキストを使う。
 *  各テストはトランザクションでロールバックされる)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CachedArticleRepositoryFameTierTest {

    @Autowired
    private CachedArticleRepository repository;

    private CachedArticle save(String title, String scope, String genre, Double fame) {
        return save(title, scope, genre, fame, "{}");
    }

    private CachedArticle save(String title, String scope, String genre, Double fame, String json) {
        CachedArticle c = new CachedArticle();
        c.setTitle(title);
        c.setScope(scope);
        c.setGenre(genre);
        c.setDataJson(json);
        c.setFameScore(fame);
        c.setCreatedAt(Instant.now());
        return repository.save(c);
    }

    private List<CachedArticle> tier(String scope, String genre, int t, int n) {
        return repository.findRandomSampleByFameRange(
            scope, genre, FameScorer.minScore(t), FameScorer.maxScoreExclusive(t), n);
    }

    @Test
    void tiersAreAbsoluteRangesNotRelativeRanks() {
        // jp:rail は全部無名 (10〜20/日) → 全て tier 5。常識レベルは 1 件も無い
        for (int i = 10; i <= 20; i++) save("rail" + i, "jp", "rail", FameScorer.viewsToScore(i));
        assertTrue(tier("jp", "rail", 1, 50).isEmpty(), "無名ばかりのバケットに tier 1 は存在しない");
        assertTrue(tier("jp", "rail", 2, 50).isEmpty());
        assertEquals(11, tier("jp", "rail", 5, 50).size());

        // 有名記事を足すとそれだけが tier 1 に入る
        save("yamanote", "jp", "rail", FameScorer.viewsToScore(1500));
        save("tokyo-sta", "jp", "rail", FameScorer.viewsToScore(3000));
        save("local-sta", "jp", "rail", FameScorer.viewsToScore(300));
        assertEquals(Set.of("yamanote", "tokyo-sta"), titles(tier("jp", "rail", 1, 50)));
        assertEquals(Set.of("local-sta"), titles(tier("jp", "rail", 2, 50)));
    }

    @Test
    void bucketAndNullScoreAreRespected() {
        save("jp-famous", "jp", "rail", FameScorer.viewsToScore(5000));
        save("world-famous", "world", "rail", FameScorer.viewsToScore(5000));
        save("nullscore", "jp", "rail", null);
        assertEquals(Set.of("jp-famous"), titles(tier("jp", "rail", 1, 50)));
        // 5 tier を合わせても null は含まれない
        Set<String> all = new HashSet<>();
        for (int t = 1; t <= 5; t++) all.addAll(titles(tier("jp", "rail", t, 50)));
        assertEquals(Set.of("jp-famous"), all);
    }

    @Test
    void nullScopeAndGenreMeansWholePool() {
        save("a", "jp", "rail", FameScorer.viewsToScore(5000));
        save("b", "world", "art", FameScorer.viewsToScore(100));
        save("c", null, null, FameScorer.viewsToScore(1));
        Set<String> all = new HashSet<>();
        for (int t = 1; t <= 5; t++) all.addAll(titles(tier(null, null, t, 50)));
        assertEquals(Set.of("a", "b", "c"), all);
        assertEquals(Set.of("a"), titles(tier(null, null, 1, 50)));
    }

    @Test
    void limitIsRespected() {
        for (int i = 0; i < 20; i++) save("x" + i, "jp", "rail", FameScorer.viewsToScore(100 + i));
        assertEquals(2, tier("jp", "rail", 3, 2).size());
    }

    @Test
    void countByFameScoreAtLeastCountsFamousOnly() {
        save("f1", "jp", "rail", FameScorer.viewsToScore(1000));
        save("f2", "jp", "rail", FameScorer.viewsToScore(300));
        save("o1", "jp", "rail", FameScorer.viewsToScore(50));
        save("n1", "jp", "rail", null);
        save("w1", "world", "rail", FameScorer.viewsToScore(1000));
        double famousMin = FameScorer.minScore(FameScorer.FAMOUS_TIER_MAX);
        assertEquals(2, repository.countByFameScoreAtLeast("jp", "rail", famousMin));
        assertEquals(3, repository.countByFameScoreAtLeast(null, null, famousMin));
    }

    @Test
    void findByFameScoreIsNullPagesUnscoredRows() {
        save("scored", "jp", "rail", 1.0);
        save("unscored1", "jp", "rail", null);
        save("unscored2", "jp", "rail", null);
        assertEquals(Set.of("unscored1", "unscored2"),
            titles(repository.findByFameScoreIsNull(PageRequest.of(0, 10))));
    }

    @Test
    void findWithoutPageViewsWalksByIdAndSkipsRowsWithViews() {
        CachedArticle a = save("noviews1", "jp", "rail", 1.0, "{\"title\":\"noviews1\",\"recentPageViews\":null}");
        save("hasviews", "jp", "rail", 1.0, "{\"title\":\"hasviews\",\"recentPageViews\":123}");
        CachedArticle b = save("noviews2", "jp", "rail", 1.0, "{\"title\":\"noviews2\",\"recentPageViews\":null}");
        assertEquals(Set.of("noviews1", "noviews2"), titles(repository.findWithoutPageViewsAfterId(0, 10)));
        assertEquals(Set.of("noviews2"), titles(repository.findWithoutPageViewsAfterId(a.getId(), 10)));
        assertTrue(repository.findWithoutPageViewsAfterId(b.getId(), 10).isEmpty());
        assertEquals(1, repository.findWithoutPageViewsAfterId(0, 1).size());
    }

    private static Set<String> titles(List<CachedArticle> list) {
        Set<String> s = new HashSet<>();
        for (CachedArticle c : list) s.add(c.getTitle());
        return s;
    }
}
