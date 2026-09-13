package com.wikiplays.service;

import com.wikiplays.dto.ArticleData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FameScorerTest {

    private static ArticleData article(int langlinks, int length, List<String> aliases, Integer dailyViews) {
        return new ArticleData("t", "intro", "full", List.of(), List.of(), Map.of(), List.of(),
            langlinks, dailyViews, length, "u", null, null, aliases);
    }

    private static ArticleData article(int langlinks, int length, List<String> aliases) {
        return article(langlinks, length, aliases, null);
    }

    @Test
    void pageViewsDecideTierWhenAvailable() {
        // 富士山・徳川家康クラス (数千/日) は常識レベル、無名の題材 (10/日) は超マニアック
        assertEquals(1, FameScorer.tierOf(FameScorer.score(article(3, 2_000, List.of(), 3000))));
        assertEquals(2, FameScorer.tierOf(FameScorer.score(article(3, 2_000, List.of(), 400))));
        assertEquals(3, FameScorer.tierOf(FameScorer.score(article(3, 2_000, List.of(), 150))));
        assertEquals(4, FameScorer.tierOf(FameScorer.score(article(3, 2_000, List.of(), 40))));
        assertEquals(5, FameScorer.tierOf(FameScorer.score(article(150, 150_000, List.of("a"), 10))));
    }

    @Test
    void tierBoundariesAreAbsoluteAndInclusiveAtLowerEnd() {
        for (int t = 1; t <= FameScorer.TIERS; t++) {
            double lo = FameScorer.minScore(t);
            double hi = FameScorer.maxScoreExclusive(t);
            assertTrue(lo < hi, "tier " + t + " range must be non-empty");
            if (t < FameScorer.TIERS) {
                assertEquals(t, FameScorer.tierOf(lo), "lower bound belongs to tier " + t);
                assertEquals(t - 1 == 0 ? 1 : t - 1, FameScorer.tierOf(hi), "upper bound belongs to the tier above");
            }
        }
        // 境界の閲覧数そのものが tier に入る
        assertEquals(1, FameScorer.tierOf(FameScorer.viewsToScore(FameScorer.TIER_MIN_DAILY_VIEWS[0])));
        assertEquals(2, FameScorer.tierOf(FameScorer.viewsToScore(FameScorer.TIER_MIN_DAILY_VIEWS[0] - 1)));
        assertEquals(5, FameScorer.tierOf(FameScorer.viewsToScore(FameScorer.TIER_MIN_DAILY_VIEWS[3] - 1)));
        assertEquals(5, FameScorer.tierOf(0.0));
    }

    @Test
    void estimateFromMetadataWhenNoPageViews() {
        double obscure = FameScorer.score(article(0, 2_000, List.of()));
        double medium = FameScorer.score(article(5, 20_000, List.of("a")));
        double famous = FameScorer.score(article(150, 150_000, List.of("a", "b", "c", "d")));
        assertTrue(obscure < medium);
        assertTrue(medium < famous);
        // 推定でも桁感は閲覧数と揃う: 世界的に有名な題材は常識レベル、無名の題材は超マニアック
        assertEquals(1, FameScorer.tierOf(famous));
        assertEquals(5, FameScorer.tierOf(obscure));
    }

    @Test
    void langlinksAloneCanOutweighLength() {
        // 短いが他言語版が多い記事 (有名な世界の主題) は、長いだけの記事より上
        double shortFamous = FameScorer.score(article(120, 8_000, List.of()));
        double longObscure = FameScorer.score(article(0, 60_000, List.of()));
        assertTrue(shortFamous > longObscure);
    }

    @Test
    void handlesNullAndNegativeGracefully() {
        assertEquals(0.0, FameScorer.score(null));
        assertTrue(FameScorer.score(article(-1, -5, null)) >= 0.0);
        assertEquals(0.0, FameScorer.score(article(0, 0, List.of(), 0)));
        // 負の閲覧数は「未取得」として推定にフォールバック
        assertEquals(FameScorer.score(article(3, 1000, null)), FameScorer.score(article(3, 1000, null, -1)));
        // 旧キャッシュは aliases が null になり得るが ArticleData 側で空リストに丸められる
        assertDoesNotThrow(() -> FameScorer.score(article(3, 1000, null)));
    }

    @Test
    void normalizeTierRejectsOutOfRange() {
        assertNull(FameScorer.normalizeTier(null));
        assertNull(FameScorer.normalizeTier(0));
        assertNull(FameScorer.normalizeTier(6));
        assertEquals(1, FameScorer.normalizeTier(1));
        assertEquals(5, FameScorer.normalizeTier(5));
    }

    @Test
    void fallbackOrderPrefersNearestThenEasier() {
        assertEquals(List.of(1, 2, 3, 4, 5), FameScorer.fallbackOrder(1));
        assertEquals(List.of(3, 2, 4, 1, 5), FameScorer.fallbackOrder(3));
        assertEquals(List.of(5, 4, 3, 2, 1), FameScorer.fallbackOrder(5));
    }
}
