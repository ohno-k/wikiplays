package com.wikiplays.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ダミーユーザーの表示名を、日本語版 Wikipedia の利用者一覧 (list=allusers) から拾ってくる。
 *
 * 手作りの単語合成 (「ねむいパンダ」など) は並ぶとパターンが見えてしまうので、
 * 実在する編集者のアカウント名を素材にする。編集履歴のある利用者に限定し、
 * bot・運営系・IP 利用者・改名済みアカウントなど「人のハンドルに見えない」名前は除外する。
 *
 * 一覧は名前順なので、ランダムな開始位置 (aufrom) から数ページ取り、さらにシャッフルして返す。
 * Wikipedia に到達できない場合は空リストを返し、呼び出し側がフォールバックする。
 */
@Component
public class WikipediaUserNameSource {

    private static final Logger log = LoggerFactory.getLogger(WikipediaUserNameSource.class);

    /** 表示名として扱う長さ。app_user.display_name は 32 文字だが、長すぎる名前は目立つので短めに。 */
    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 20;

    /** 1 リクエストで取る件数と、1 回の fetch で許すリクエスト数の上限。 */
    private static final int PAGE_SIZE = 100;
    private static final int MAX_REQUESTS = 15;
    /** 1 ページから採用する最大数 (同じ並びをそのまま持ってこないように間引く)。 */
    private static final int PER_PAGE_KEEP = 25;

    /** 明らかに人のハンドルではない名前 (bot、運営、退会・改名済み、テスト用など)。 */
    private static final Pattern EXCLUDED_WORDS = Pattern.compile(
        "(?iu)bot|ボット|wiki|ウィキ|wmf|wmjp|admin|sysop|管理者|steward|staff|"
        + "renamed|vanished|deleted|removed|改名|退会|廃止|削除|"
        + "test|テスト|sample|dummy|example|"
        + "sock|abuse|vandal|spam|荒らし|block|ブロック|"
        + "script|maintenance|import|migration|placeholder|official|公式");

    /** 記号だらけ・IP アドレス・括弧付き ("(WMF)" 等) を弾くための文字種チェック。 */
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[\\p{Cntrl}()（）\\[\\]{}<>|/\\\\@#:;=~\"'`$%&*+^]");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");
    private static final Pattern REPEATED_CHAR = Pattern.compile("(.)\\1{3,}");

    private static final String LATIN_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LATIN_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String HIRAGANA = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわ";
    private static final String KATAKANA = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワ";
    private static final String KANJI = "山田中小大高木井佐鈴青赤白黒東西南北春夏秋冬雪月星空海花風光森川野村石松竹梅桜藤金銀";

    private final WebClient wikipediaWebClient;
    private final Random random = new Random();

    public WikipediaUserNameSource(WebClient wikipediaWebClient) {
        this.wikipediaWebClient = wikipediaWebClient;
    }

    /**
     * 使えそうな利用者名を最大 wanted 件、重複なし・シャッフル済みで返す。
     * 到達できない・足りない場合は取れた分だけ返す (空もあり得る)。
     */
    public List<String> fetch(int wanted) {
        Set<String> out = new LinkedHashSet<>();
        if (wanted <= 0) return new ArrayList<>();
        int requests = Math.min(MAX_REQUESTS, wanted / PER_PAGE_KEEP + 3);
        int failures = 0;
        for (int i = 0; i < requests && out.size() < wanted; i++) {
            try {
                List<String> page = fetchPage(randomStartKey());
                Collections.shuffle(page, random);
                out.addAll(page.subList(0, Math.min(PER_PAGE_KEEP, page.size())));
            } catch (Exception e) {
                failures++;
                log.debug("allusers fetch failed: {}", e.getMessage());
                if (failures >= 3) break;  // 到達できないなら早めに諦める
            }
        }
        if (failures > 0 && out.isEmpty()) {
            log.warn("WikipediaUserNameSource: could not fetch user names from Wikipedia ({} failures)", failures);
        }
        List<String> list = new ArrayList<>(out);
        Collections.shuffle(list, random);
        return list.size() > wanted ? new ArrayList<>(list.subList(0, wanted)) : list;
    }

    /** aufrom から名前順に 1 ページ取り、使える名前だけにして返す。 */
    List<String> fetchPage(String from) {
        JsonNode json = wikipediaWebClient.get()
            .uri(uri -> uri.path("/w/api.php")
                .queryParam("action", "query")
                .queryParam("format", "json")
                .queryParam("list", "allusers")
                .queryParam("auwitheditsonly", 1)
                .queryParam("auexcludegroup", "bot")
                .queryParam("aulimit", PAGE_SIZE)
                .queryParam("aufrom", from)
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
        if (json == null) throw new IllegalStateException("allusers が空応答");
        return parse(json);
    }

    /** allusers 応答から採用可能な名前だけを取り出す。 */
    static List<String> parse(JsonNode json) {
        List<String> names = new ArrayList<>();
        for (JsonNode u : json.path("query").path("allusers")) {
            String name = u.path("name").asText(null);
            if (isUsable(name)) names.add(name.trim());
        }
        return names;
    }

    /** 実ユーザーの表示名として自然に見える名前か。 */
    static boolean isUsable(String raw) {
        if (raw == null) return false;
        String name = raw.trim();
        int len = name.codePointCount(0, name.length());
        if (len < MIN_LENGTH || len > MAX_LENGTH) return false;
        if (!HAS_LETTER.matcher(name).find()) return false;
        if (DISALLOWED_CHARS.matcher(name).find()) return false;
        if (IPV4.matcher(name).matches()) return false;
        if (EXCLUDED_WORDS.matcher(name).find()) return false;
        if (REPEATED_CHAR.matcher(name).find()) return false;
        if (name.chars().filter(Character::isDigit).count() > len / 2) return false;  // 数字だらけ
        if (name.contains("  ") || name.contains("_ ") || name.contains(" _")) return false;
        return true;
    }

    /**
     * ランダムな開始キー。1 文字だと毎回同じ先頭ページになるので、ラテン文字は 2 文字、
     * かなは 1〜2 文字にして一覧の途中から読み始める。
     */
    String randomStartKey() {
        double r = random.nextDouble();
        if (r < 0.55) {
            return "" + pick(LATIN_UPPER) + pick(LATIN_LOWER);
        } else if (r < 0.72) {
            return random.nextBoolean() ? "" + pick(HIRAGANA) + pick(HIRAGANA) : "" + pick(HIRAGANA);
        } else if (r < 0.87) {
            return random.nextBoolean() ? "" + pick(KATAKANA) + pick(KATAKANA) : "" + pick(KATAKANA);
        } else {
            return "" + pick(KANJI);
        }
    }

    private char pick(String s) {
        return s.charAt(random.nextInt(s.length()));
    }
}
