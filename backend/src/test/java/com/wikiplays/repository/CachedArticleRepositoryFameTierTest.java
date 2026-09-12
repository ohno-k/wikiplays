package com.wikiplays.repository;

import com.wikiplays.entity.CachedArticle;
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
 * 知名度 tier 抽出クエリ (NTILE) が H2 上で意図どおりに動くことの確認。
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
        CachedArticle c = new CachedArticle();
        c.setTitle(title);
        c.setScope(scope);
        c.setGenre(genre);
        c.setDataJson("{}");
        c.setFameScore(fame);
        c.setCreatedAt(Instant.now());
        return repository.save(c);
    }

    @Test
    void tiersSplitBucketByFameScoreDescending() {
        // jp:rail に 10 件 (スコア 10..1)、別バケットに 1 件、未計算 1 件
        for (int i = 10; i >= 1; i--) save("rail" + i, "jp", "rail", (double) i);
        save("world1", "world", "rail", 100.0);
        save("nullscore", "jp", "rail", null);

        List<CachedArticle> tier1 = repository.findRandomSampleByFameTier("jp", "rail", 1, 50);
        List<CachedArticle> tier5 = repository.findRandomSampleByFameTier("jp", "rail", 5, 50);

        assertEquals(Set.of("rail10", "rail9"), titles(tier1), "tier 1 = 最もスコアの高い 2 件");
        assertEquals(Set.of("rail2", "rail1"), titles(tier5), "tier 5 = 最もスコアの低い 2 件");

        // 5 tier を合わせるとバケット内のスコア付き記事を全て網羅し、null は含まれない
        Set<String> all = new HashSet<>();
        for (int t = 1; t <= 5; t++) all.addAll(titles(repository.findRandomSampleByFameTier("jp", "rail", t, 50)));
        assertEquals(10, all.size());
        assertFalse(all.contains("nullscore"));
        assertFalse(all.contains("world1"));
    }

    @Test
    void nullScopeAndGenreMeansWholePool() {
        save("a", "jp", "rail", 3.0);
        save("b", "world", "art", 2.0);
        save("c", null, null, 1.0);
        Set<String> all = new HashSet<>();
        for (int t = 1; t <= 5; t++) all.addAll(titles(repository.findRandomSampleByFameTier(null, null, t, 50)));
        assertEquals(Set.of("a", "b", "c"), all);
    }

    @Test
    void limitIsRespected() {
        for (int i = 0; i < 20; i++) save("x" + i, "jp", "rail", (double) i);
        assertEquals(2, repository.findRandomSampleByFameTier("jp", "rail", 3, 2).size());
    }

    @Test
    void findByFameScoreIsNullPagesUnscoredRows() {
        save("scored", "jp", "rail", 1.0);
        save("unscored1", "jp", "rail", null);
        save("unscored2", "jp", "rail", null);
        assertEquals(Set.of("unscored1", "unscored2"),
            titles(repository.findByFameScoreIsNull(PageRequest.of(0, 10))));
    }

    private static Set<String> titles(List<CachedArticle> list) {
        Set<String> s = new HashSet<>();
        for (CachedArticle c : list) s.add(c.getTitle());
        return s;
    }
}
