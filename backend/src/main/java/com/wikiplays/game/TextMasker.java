package com.wikiplays.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 記事本文からタイトル・別名・読み仮名などの手がかりを伏せるユーティリティ。
 * フロントエンドの masking.ts と同じ挙動をサーバー側で提供する (段落はサーバーで
 * マスクしてから配信するので、答えがブラウザに渡らない)。
 */
public final class TextMasker {

    private TextMasker() {}

    public static final String MASK_TOKEN = "■■■■";

    /** 末尾に来るメタセクション以降を切り捨てる。 */
    private static final List<String> META_SECTION_HEADINGS = List.of(
        "関連項目", "参考文献", "外部リンク", "脚注", "注釈", "出典",
        "ギャラリー", "関連書籍", "関連作品", "参考資料", "注"
    );

    private static final Pattern SENTENCE_END = Pattern.compile("[。．！？]");
    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n{2,}");
    private static final Pattern PAREN_AFTER_MASK = Pattern.compile(
        "([「『]?)(?:" + Pattern.quote(MASK_TOKEN) + "[ 　]?)+([」』]?)\\s*[（(][^（()）]{0,80}[）)]"
    );

    /** タイトルの各文字間に最大 1 個の空白を許容し、大文字小文字を無視する正規表現。 */
    static Pattern tolerantPattern(String s) {
        List<String> parts = new ArrayList<>();
        s.codePoints().forEach(cp -> parts.add(Pattern.quote(Character.toString(cp))));
        if (parts.isEmpty()) return Pattern.compile("(?!)");
        return Pattern.compile(String.join("[ 　]?", parts), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * 本文中で「姓 名」の形が見つかればタイトルを人名と判定し、姓・名を返す。
     */
    static List<String> detectPersonNameParts(String text, String title) {
        int[] cps = title.codePoints().toArray();
        if (cps.length < 3) return null;
        for (int i = 1; i < cps.length; i++) {
            String left = new String(cps, 0, i);
            String right = new String(cps, i, cps.length - i);
            if (left.codePointCount(0, left.length()) < 2 || right.codePointCount(0, right.length()) < 2) continue;
            Pattern p = Pattern.compile(Pattern.quote(left) + "[ 　]+" + Pattern.quote(right),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            if (p.matcher(text).find()) return List.of(left, right);
        }
        return null;
    }

    /** タイトルの括弧前部分 (曖昧さ回避語を除いた本体)。 */
    public static String coreTitle(String title) {
        if (title == null) return "";
        String main = title.split("[（(]", 2)[0].trim();
        if (main.isEmpty()) main = title.replaceAll("[（(][^（()）]*[）)]", "").trim();
        if (main.isEmpty()) main = title.trim();
        return main;
    }

    /** タイトル本体と別名を本文からマスクする。 */
    public static String maskTitle(String text, String title, List<String> aliases) {
        if (text == null || text.isEmpty() || title == null || title.isEmpty()) return text;

        // 1. タイトル本体 (文字間の任意空白を許容)
        String out = tolerantPattern(title).matcher(text).replaceAll(Matcher.quoteReplacement(MASK_TOKEN));

        // 2. 人名分割: 「姓 名」で現れていたら姓・名を個別マスク
        List<String> parts = detectPersonNameParts(text, title);
        if (parts != null) {
            for (String p : parts) {
                out = Pattern.compile(Pattern.quote(p), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(out).replaceAll(Matcher.quoteReplacement(MASK_TOKEN));
            }
        }

        // 3. 「タイトル (曖昧さ回避)」の括弧前部分
        String core = coreTitle(title);
        if (!core.isEmpty() && !core.equals(title) && core.codePointCount(0, core.length()) >= 2) {
            out = tolerantPattern(core).matcher(out).replaceAll(Matcher.quoteReplacement(MASK_TOKEN));
        }

        // 4. 別名 (リダイレクト)。短すぎるものは誤爆が多いので 2 文字以上のみ
        if (aliases != null) {
            Set<String> seen = new HashSet<>();
            for (String a : aliases) {
                if (a == null) continue;
                String t = a.trim();
                if (t.codePointCount(0, t.length()) < 2 || !seen.add(t)) continue;
                out = tolerantPattern(t).matcher(out).replaceAll(Matcher.quoteReplacement(MASK_TOKEN));
            }
        }

        // 5. マスクトークン直後の括弧書き (読み仮名・英名など) を丸ごと消す
        out = PAREN_AFTER_MASK.matcher(out).replaceAll("$1" + MASK_TOKEN + "$2");
        return out;
    }

    public static String trimMetaSections(String text) {
        if (text == null || text.isEmpty()) return text;
        int cutAt = text.length();
        for (String heading : META_SECTION_HEADINGS) {
            Matcher m = Pattern.compile("\\n" + Pattern.quote(heading) + "\\s*\\n").matcher(text);
            if (m.find() && m.start() < cutAt) cutAt = m.start();
        }
        return text.substring(0, cutAt);
    }

    /**
     * 記事本文を段落単位に分割し、見出し行 (sections と一致するもの) を除外する。
     */
    public static List<String> splitParagraphs(String fullText, List<String> headingTitles) {
        List<String> out = new ArrayList<>();
        if (fullText == null || fullText.isEmpty()) return out;
        Set<String> headingSet = new HashSet<>();
        if (headingTitles != null) for (String h : headingTitles) headingSet.add(h.trim());
        for (String raw : PARAGRAPH_SPLIT.split(trimMetaSections(fullText))) {
            String p = raw.trim();
            if (p.isEmpty()) continue;
            if (headingSet.contains(p)) continue;
            if (p.length() < 15 && !SENTENCE_END.matcher(p).find()) continue;
            out.add(p);
        }
        return out;
    }
}
