package com.wikiplays.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** prop=pageviews の応答 ("日付" → 閲覧数 | null) から 1 日平均を求める部分の確認。 */
class WikipediaServicePageViewsTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static JsonNode page(String pageviewsJson) throws Exception {
        return M.readTree("{\"pageid\":1,\"title\":\"t\"" + (pageviewsJson == null ? "" : ",\"pageviews\":" + pageviewsJson) + "}");
    }

    @Test
    void averagesOverDaysWithData() throws Exception {
        // 直近の日はまだ集計されておらず null で返ってくる
        JsonNode p = page("{\"2026-09-10\":1000,\"2026-09-11\":2000,\"2026-09-12\":null}");
        assertEquals(1500, WikipediaService.parseDailyPageViews(p));
    }

    @Test
    void roundsToNearestInteger() throws Exception {
        JsonNode p = page("{\"a\":1,\"b\":2}");
        assertEquals(2, WikipediaService.parseDailyPageViews(p)); // 1.5 → 2
    }

    @Test
    void returnsNullWhenNoData() throws Exception {
        assertNull(WikipediaService.parseDailyPageViews(page(null)));
        assertNull(WikipediaService.parseDailyPageViews(page("{}")));
        assertNull(WikipediaService.parseDailyPageViews(page("{\"a\":null}")));
        assertNull(WikipediaService.parseDailyPageViews(null));
    }
}
