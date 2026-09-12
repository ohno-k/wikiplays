package com.wikiplays.game;

import com.wikiplays.dto.ArticleData;
import com.wikiplays.dto.Section;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionServiceTest {

    private static SessionState.Question question(int paragraphs) {
        SessionState.Question q = new SessionState.Question();
        for (int i = 0; i < paragraphs; i++) q.paragraphs.add("p" + i);
        q.startedAtMs = 0;
        return q;
    }

    @Test
    void scoreDependsOnRevealCountAndLife() {
        assertEquals(1000, GameSessionService.scoreFor(true, 1, false, 5, 5));
        assertEquals(800, GameSessionService.scoreFor(true, 2, false, 5, 5));
        assertEquals(100, GameSessionService.scoreFor(true, 9, false, 5, 5));
        assertEquals(500, GameSessionService.scoreFor(true, 1, true, 5, 5));
        // 部分点は最大の半分まで
        assertEquals(300, GameSessionService.scoreFor(false, 1, false, 3, 5));
        assertEquals(0, GameSessionService.scoreFor(false, 1, false, 0, 0));
    }

    @Test
    void revealFollowsElapsedTimeUntilLocked() {
        SessionState.Question q = question(6);
        int interval = 10_000;
        assertEquals(1, GameSessionService.effectiveRevealed(q, interval, 0));
        assertEquals(1, GameSessionService.effectiveRevealed(q, interval, 9_000 + GameSessionService.GRACE_MS));
        assertEquals(2, GameSessionService.effectiveRevealed(q, interval, 10_000 + GameSessionService.GRACE_MS));
        assertEquals(4, GameSessionService.effectiveRevealed(q, interval, 30_000 + GameSessionService.GRACE_MS));
        // 段落数を超えない
        assertEquals(6, GameSessionService.effectiveRevealed(q, interval, 10_000_000));
        // 明示的な開示の方が多ければそちら
        q.revealed = 3;
        assertEquals(3, GameSessionService.effectiveRevealed(q, interval, 0));
        // 入力開始で固定
        q.lockedRevealed = 2;
        assertEquals(2, GameSessionService.effectiveRevealed(q, interval, 10_000_000));
    }

    @Test
    void prepareBuildsMaskedParagraphsAndOptions() {
        GameSessionService svc = new GameSessionService(null, null, null, null, null, null, null, null, null, null);
        ArticleData a = new ArticleData(
            "東京タワー",
            "東京タワー（とうきょうタワー）は電波塔である。",
            "東京タワー（とうきょうタワー）は電波塔である。港区にある。\n\n\n歴史\n\n\n1958年に完成した。高さは333メートル。",
            List.of(new Section("歴史", 1)),
            List.of(), Map.of(), List.of("東京都の塔"), 10, null, 5000,
            "https://ja.wikipedia.org/wiki/東京タワー", 1958, "completed", List.of("東京タワ")
        );
        SessionState.Question q = svc.prepare(a, false);
        assertNotNull(q);
        assertEquals(2, q.paragraphs.size());
        for (String p : q.paragraphs) assertFalse(p.contains("東京タワー"));
        assertEquals(5, q.chars.size());
        assertEquals(4, q.options.get(0).size());
        assertTrue(q.options.get(4).contains("ー"));
    }

    @Test
    void prepareRejectsShortArticles() {
        GameSessionService svc = new GameSessionService(null, null, null, null, null, null, null, null, null, null);
        ArticleData a = new ArticleData("X", "短い。", "短い。", List.of(), List.of(), Map.of(), List.of(), 0, null, 10,
            "u", null, null, List.of());
        assertNull(svc.prepare(a, false));
        assertNotNull(svc.prepare(a, true), "デイリーは 1 段落でも許容");
    }
}
