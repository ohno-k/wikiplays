package com.wikiplays.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 「1 文字ずつ 4 択で答える」入力方式のための、答え文字列の分解と選択肢生成。
 */
public final class CharInput {

    private CharInput() {}

    /** 答え判定で読み飛ばす文字 (記号・空白)。プレイヤーの入力対象にならない。 */
    public static final Pattern SKIP_CHAR = Pattern.compile("[・＝＋\\s\\-/「」『』（）()【】\\[\\]\"',.、。!！?？]");

    private static final String HIRAGANA = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽぁぃぅぇぉっゃゅょー";
    private static final String KATAKANA = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポァィゥェォッャュョー";
    private static final String ALPHA_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String ALPHA_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String KANJI_FILLER = "人本年日中大国生学者一二三月時山田";

    public static final int OPTION_COUNT = 4;

    public static boolean isSkip(String c) {
        return SKIP_CHAR.matcher(c).matches();
    }

    /** タイトルから入力対象の文字配列を作る (曖昧さ回避の括弧を除く)。 */
    public static List<String> answerChars(String title) {
        List<String> out = new ArrayList<>();
        TextMasker.coreTitle(title).codePoints().forEach(cp -> out.add(Character.toString(cp)));
        return out;
    }

    private static boolean isKanji(String c) {
        return Character.UnicodeScript.of(c.codePointAt(0)) == Character.UnicodeScript.HAN;
    }

    private static List<String> chars(String s) {
        List<String> out = new ArrayList<>();
        s.codePoints().forEach(cp -> out.add(Character.toString(cp)));
        return out;
    }

    /** 1 文字分の 4 択 (正解 1 + 同種のダミー 3) を生成する。 */
    public static List<String> options(String target, String fullExtract, Random random) {
        List<String> pool = new ArrayList<>();
        int cp = target.codePointAt(0);
        if (isKanji(target)) {
            Set<String> kanji = new LinkedHashSet<>();
            if (fullExtract != null) {
                fullExtract.codePoints()
                    .filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)
                    .forEach(c -> kanji.add(Character.toString(c)));
            }
            kanji.remove(target);
            pool.addAll(kanji);
            if (pool.size() < 3) {
                for (String f : chars(KANJI_FILLER)) {
                    if (!f.equals(target) && !pool.contains(f)) pool.add(f);
                    if (pool.size() >= 10) break;
                }
            }
        } else if (cp >= 0x3040 && cp <= 0x309F) {
            pool = chars(HIRAGANA);
        } else if (cp >= 0x30A0 && cp <= 0x30FF) {
            pool = chars(KATAKANA);
        } else if (cp >= 'a' && cp <= 'z') {
            pool = chars(ALPHA_LOWER);
        } else if (cp >= 'A' && cp <= 'Z') {
            pool = chars(ALPHA_UPPER);
        } else if (cp >= '0' && cp <= '9') {
            pool = chars(DIGITS);
        } else {
            pool = new ArrayList<>(List.of("・", "＝", "＋", "ー", "〜", "&"));
        }
        pool.remove(target);
        Collections.shuffle(pool, random);
        List<String> result = new ArrayList<>();
        result.add(target);
        result.addAll(pool.subList(0, Math.min(3, pool.size())));
        Collections.shuffle(result, random);
        return result;
    }
}
