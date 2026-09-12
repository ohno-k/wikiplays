package com.wikiplays.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CharInputTest {

    @Test
    void answerCharsStripsDisambiguation() {
        assertEquals(List.of("東", "京", "タ", "ワ", "ー"), CharInput.answerChars("東京タワー (電波塔)"));
    }

    @Test
    void optionsContainTargetAndAreDistinct() {
        Random r = new Random(1);
        for (String target : List.of("東", "あ", "ア", "a", "Z", "7", "・")) {
            List<String> opts = CharInput.options(target, "東京都港区の電波塔である。", r);
            assertTrue(opts.contains(target), target);
            assertEquals(4, opts.size(), target);
            assertEquals(4, new HashSet<>(opts).size(), target);
        }
    }

    @Test
    void kanjiOptionsComeFromArticleText() {
        List<String> opts = CharInput.options("東", "東京都港区にある電波塔。", new Random(2));
        for (String o : opts) {
            if (!o.equals("東")) assertTrue("京都港区電波塔".contains(o), o);
        }
    }

    @Test
    void skipCharacters() {
        assertTrue(CharInput.isSkip("・"));
        assertTrue(CharInput.isSkip(" "));
        assertFalse(CharInput.isSkip("ー"));
    }
}
