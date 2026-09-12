package com.wikiplays.service;

import com.wikiplays.dto.ArticleData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArticleFilterTest {

    private final ArticleFilter filter = new ArticleFilter();

    private static ArticleData article(String title, String body, List<String> categories) {
        return new ArticleData(title, body, body, List.of(), List.of(), Map.of(), categories, 3, null, 1000,
            "u", null, null, List.of());
    }

    private static final String LONG_BODY = "これは十分な長さのある冒頭文です。".repeat(8);

    @Test
    void allowsOrdinaryArticle() {
        assertTrue(filter.isAllowed(article("東京タワー", LONG_BODY, List.of("東京都の塔"))));
    }

    @Test
    void blocksIncidentTitlesAndCategories() {
        assertFalse(filter.isAllowed(article("○○事件", LONG_BODY, List.of())));
        assertFalse(filter.isAllowed(article("普通の題名", LONG_BODY, List.of("日本の殺人事件"))));
        assertFalse(filter.isAllowed(article("日本の山の一覧", LONG_BODY, List.of())));
    }

    @Test
    void livingPersonsAreAllowedUnlessScandalous() {
        assertTrue(filter.isAllowed(article("山田太郎", LONG_BODY, List.of("存命人物", "日本の野球選手"))));
        String scandal = LONG_BODY + "逮捕された。起訴された。";
        assertFalse(filter.isAllowed(article("山田太郎", scandal, List.of("存命人物"))));
        // 故人・非人物は 5 回未満なら許容
        assertTrue(filter.isAllowed(article("ある事柄", scandal, List.of("日本の歴史"))));
    }

    @Test
    void blocksTooShortIntro() {
        assertFalse(filter.isAllowed(article("短い", "短い。", List.of())));
    }
}
