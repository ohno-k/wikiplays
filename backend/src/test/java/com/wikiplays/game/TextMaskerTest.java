package com.wikiplays.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextMaskerTest {

    @Test
    void masksTitleAndReadingParenthesis() {
        String text = "東京タワー（とうきょうタワー、英: Tokyo Tower）は、東京都港区にある電波塔である。";
        String out = TextMasker.maskTitle(text, "東京タワー", List.of());
        assertFalse(out.contains("東京タワー"));
        assertFalse(out.contains("とうきょうタワー"));
        assertTrue(out.startsWith(TextMasker.MASK_TOKEN + "は、"));
    }

    @Test
    void toleratesSpaceBetweenCharacters() {
        String out = TextMasker.maskTitle("相原 信行は体操選手。相原体操クラブを設立。", "相原信行", List.of());
        assertFalse(out.contains("相原"));
        assertFalse(out.contains("信行"));
    }

    @Test
    void masksAliasesButIgnoresSingleChars() {
        String out = TextMasker.maskTitle("通称ジブリ。略称は「ジ」。", "スタジオジブリ", List.of("ジブリ", "ジ"));
        assertFalse(out.contains("ジブリ"));
        assertTrue(out.contains("「ジ」"));
    }

    @Test
    void masksDisambiguationCore() {
        String out = TextMasker.maskTitle("オーロラは極地で見られる。", "オーロラ (現象)", List.of());
        assertFalse(out.contains("オーロラ"));
    }

    @Test
    void coreTitleHandlesLeadingParenthesis() {
        assertEquals("オーロラ", TextMasker.coreTitle("(94) オーロラ"));
        assertEquals("東京", TextMasker.coreTitle("東京 (映画)"));
    }

    @Test
    void splitParagraphsDropsHeadingsAndMetaSections() {
        String text = "冒頭の段落です。\n\n\n歴史\n\n\n歴史の段落です。\n\n\n脚注\n\n\n[1] 出典です。";
        List<String> ps = TextMasker.splitParagraphs(text, List.of("歴史", "脚注"));
        assertEquals(List.of("冒頭の段落です。", "歴史の段落です。"), ps);
    }
}
