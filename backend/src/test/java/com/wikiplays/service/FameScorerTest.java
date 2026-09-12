package com.wikiplays.service;

import com.wikiplays.dto.ArticleData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FameScorerTest {

    private static ArticleData article(int langlinks, int length, List<String> aliases) {
        return new ArticleData("t", "intro", "full", List.of(), List.of(), Map.of(), List.of(),
            langlinks, null, length, "u", null, null, aliases);
    }

    @Test
    void moreLanglinksLengthAndAliasesScoreHigher() {
        double obscure = FameScorer.score(article(0, 2_000, List.of()));
        double medium = FameScorer.score(article(5, 20_000, List.of("a")));
        double famous = FameScorer.score(article(150, 150_000, List.of("a", "b", "c", "d")));
        assertTrue(obscure < medium);
        assertTrue(medium < famous);
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
        assertEquals(0.0, FameScorer.score(article(-1, -5, null)));
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
}
