package com.wikiplays.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wikiplays.dto.ArticleData;
import com.wikiplays.dto.Section;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日本語版 Wikipedia の MediaWiki API クライアント。
 * 1 つの記事についてゲームに必要な各種データレイヤーを取得する。
 */
@Service
public class WikipediaService {

    /** インフォボックス内の Wiki テンプレート (例: {{生年月日と年齢|1879|3|14}}) から年を取り出す用。 */
    private static final Pattern ANY_YEAR_NUMBER = Pattern.compile("(?<!\\d)(\\d{3,4})(?!\\d)");

    private final WebClient wikipediaWebClient;

    public WikipediaService(WebClient wikipediaWebClient) {
        this.wikipediaWebClient = wikipediaWebClient;
    }

    /**
     * 指定スコープ・ジャンルからランダムなページタイトルを取得。
     * 該当する組み合わせが定義されていない、またはどのカテゴリでもメンバーが取得できなければ
     * 通常のランダム取得にフォールバックする。
     *
     * @param scope "jp" / "world" / null
     * @param genre "rail" / "history" / ... / null
     */
    public String fetchRandomTitleByGenre(String scope, String genre) {
        List<String> categories = GenreCatalog.getCategories(scope, genre);
        if (categories.isEmpty()) return fetchRandomTitle();
        List<String> shuffledCats = new ArrayList<>(categories);
        Collections.shuffle(shuffledCats);
        for (String cat : shuffledCats) {
            try {
                List<String> members = fetchCategoryMembers(cat, 50);
                if (!members.isEmpty()) {
                    Collections.shuffle(members);
                    return members.get(0);
                }
            } catch (Exception ignored) {
                // 次のカテゴリで再試行
            }
        }
        return fetchRandomTitle();
    }

    /** 標準名前空間からランダムなページタイトルを 1 件取得。 */
    public String fetchRandomTitle() {
        JsonNode json = wikipediaWebClient.get()
            .uri(uri -> uri.path("/w/api.php")
                .queryParam("action", "query")
                .queryParam("format", "json")
                .queryParam("list", "random")
                .queryParam("rnnamespace", 0)
                .queryParam("rnlimit", 1)
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        if (json == null) throw new IllegalStateException("Wikipedia random API が空応答");
        JsonNode arr = json.path("query").path("random");
        if (!arr.isArray() || arr.isEmpty()) throw new IllegalStateException("random 配列が空");
        return arr.get(0).path("title").asText();
    }

    /**
     * 指定タイトルの記事について、ゲームに必要な全レイヤーをまとめて取得する。
     * MediaWiki API の prop を複数指定し、可能な範囲で 1 リクエストにまとめる。
     */
    public ArticleData fetchArticleData(String title) {
        // 本文 + 構造化メタデータ
        JsonNode bulk = wikipediaWebClient.get()
            .uri(uri -> uri.path("/w/api.php")
                .queryParam("action", "query")
                .queryParam("format", "json")
                .queryParam("prop", "extracts|images|categories|langlinks|info|redirects|pageviews")
                .queryParam("pvipdays", PAGEVIEW_DAYS)
                .queryParam("rdlimit", "max")
                .queryParam("rdnamespace", 0)
                .queryParam("explaintext", 1)
                .queryParam("exsectionformat", "plain")
                .queryParam("imlimit", 20)
                .queryParam("cllimit", "max")
                .queryParam("clshow", "!hidden")
                .queryParam("lllimit", "max")
                .queryParam("redirects", 1)
                .queryParam("titles", title)
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        if (bulk == null) throw new IllegalStateException("bulk query が空応答: " + title);
        JsonNode page = firstPage(bulk);
        if (page == null || page.has("missing")) {
            throw new IllegalStateException("ページが存在しない: " + title);
        }

        String resolvedTitle = page.path("title").asText(title);
        String fullExtract = page.path("extract").asText("");
        String introExtract = extractIntro(fullExtract);

        List<String> categories = new ArrayList<>();
        for (JsonNode c : page.path("categories")) {
            String name = c.path("title").asText("");
            if (name.startsWith("Category:")) name = name.substring("Category:".length());
            if (!name.isEmpty()) categories.add(name);
        }

        List<String> imageNames = new ArrayList<>();
        for (JsonNode img : page.path("images")) {
            String name = img.path("title").asText("");
            if (!name.isEmpty() && !name.endsWith(".svg")) imageNames.add(name);
        }
        List<String> imageUrls = fetchImageUrls(imageNames);

        int languageLinkCount = page.path("langlinks").isArray() ? page.path("langlinks").size() : 0;
        int articleLength = page.path("length").asInt(0);
        List<String> aliases = extractAliases(page, resolvedTitle);

        List<Section> sections = fetchSections(resolvedTitle);
        Map<String, String> infobox = fetchInfobox(resolvedTitle);
        ExtractedYearInfo yearInfo = extractYearInfo(infobox, introExtract);

        return new ArticleData(
            resolvedTitle,
            introExtract,
            fullExtract,
            sections,
            imageUrls,
            infobox,
            categories,
            languageLinkCount,
            parseDailyPageViews(page),
            articleLength,
            buildPageUrl(resolvedTitle),
            yearInfo == null ? null : yearInfo.year,
            yearInfo == null ? null : yearInfo.kind,
            aliases
        );
    }

    /** 閲覧数を平均する日数 (MediaWiki の prop=pageviews が返せる上限)。 */
    static final int PAGEVIEW_DAYS = 60;

    /** 1 回の prop=pageviews 呼び出しで指定できるタイトル数の上限。 */
    private static final int PAGEVIEW_BATCH = 50;

    /** CirrusSearch のクエリ文字列長の上限 (超えるとエラー)。カテゴリ名を束ねるときの目安。 */
    private static final int SEARCH_QUERY_MAX_CHARS = 280;

    /**
     * prop=pageviews の結果 ("日付" → 閲覧数 | null) から 1 日あたりの平均閲覧数を求める。
     * 1 日分も値が無ければ null (未取得扱い)。
     */
    static Integer parseDailyPageViews(JsonNode page) {
        if (page == null) return null;
        JsonNode pv = page.path("pageviews");
        if (!pv.isObject()) return null;
        long total = 0;
        int days = 0;
        var it = pv.fields();
        while (it.hasNext()) {
            JsonNode v = it.next().getValue();
            if (v == null || v.isNull() || !v.isNumber()) continue;
            total += Math.max(0, v.asLong());
            days++;
        }
        if (days == 0) return null;
        return (int) Math.min(Integer.MAX_VALUE, Math.round((double) total / days));
    }

    /**
     * 複数タイトルの 1 日あたり平均閲覧数をまとめて取得する (旧キャッシュのバックフィル用)。
     * 戻り値のキーは Wikipedia が返す正規化後のタイトル。閲覧数が取れなかった記事は含まれない。
     */
    public Map<String, Integer> fetchDailyPageViews(List<String> titles) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int from = 0; from < titles.size(); from += PAGEVIEW_BATCH) {
            List<String> batch = titles.subList(from, Math.min(titles.size(), from + PAGEVIEW_BATCH));
            String joined = String.join("|", batch);
            JsonNode json = wikipediaWebClient.get()
                .uri(uri -> uri.path("/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("format", "json")
                    .queryParam("prop", "pageviews")
                    .queryParam("pvipdays", PAGEVIEW_DAYS)
                    .queryParam("titles", joined)
                    .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            if (json == null) continue;
            for (JsonNode page : json.path("query").path("pages")) {
                String title = page.path("title").asText("");
                Integer views = parseDailyPageViews(page);
                if (!title.isEmpty() && views != null) out.put(title, views);
            }
        }
        return out;
    }

    /**
     * 指定カテゴリ (複数可) に直接属する記事を「被リンク数の多い順」に返す。
     * CirrusSearch の incategory: と srsort=incoming_links_desc を使う。
     * 被リンク数の多い記事 = 他の記事から頻繁に言及される主題 = 誰でも知っている題材の近似。
     *
     * @param categories "Category:" 接頭辞なしのカテゴリ名。複数指定は OR
     * @param offset     何件目から (同じ上位記事ばかりにならないよう呼び出し側でずらす)
     */
    public List<String> fetchMostLinkedTitles(List<String> categories, int offset, int limit) {
        if (categories.isEmpty()) return List.of();
        String query = "incategory:\"" + String.join("|", categories) + "\"";
        JsonNode json = wikipediaWebClient.get()
            .uri(uri -> uri.path("/w/api.php")
                .queryParam("action", "query")
                .queryParam("format", "json")
                .queryParam("list", "search")
                .queryParam("srsearch", query)
                .queryParam("srsort", "incoming_links_desc")
                .queryParam("srnamespace", 0)
                .queryParam("srlimit", Math.min(Math.max(limit, 1), 50))
                .queryParam("sroffset", Math.max(offset, 0))
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        List<String> out = new ArrayList<>();
        if (json == null) return out;
        for (JsonNode hit : json.path("query").path("search")) {
            String t = hit.path("title").asText("");
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /**
     * カテゴリ木 (親 + 直下のサブカテゴリ 1 段) から被リンク数の多い記事を集める。
     * ジャンルのカテゴリは「日本の鉄道路線」のような入れ子の親であることが多く、
     * 有名な記事 (山手線 など) はサブカテゴリ側にいるため、親だけ検索しても拾えない。
     *
     * 親カテゴリの上位、次にサブカテゴリを数個ずつ束ねた検索の上位、の順で返す (重複なし)。
     *
     * @param offset 各検索の先頭から飛ばす件数 (呼ぶたびに増やして深い順位まで掘る)
     */
    public List<String> fetchMostLinkedInCategoryTree(String category, int offset, int perQuery) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        try {
            out.addAll(fetchMostLinkedTitles(List.of(category), offset, perQuery));
        } catch (Exception ignored) {
            // サブカテゴリ側で再試行
        }
        List<String> subcats;
        try {
            subcats = fetchSubcategories(category);
        } catch (Exception e) {
            return new ArrayList<>(out);
        }
        Collections.shuffle(subcats, random);
        // クエリ長の上限内でサブカテゴリを束ね、最大 3 回まで検索する
        int queries = 0;
        List<String> batch = new ArrayList<>();
        int batchChars = 0;
        for (String sub : subcats) {
            if (queries >= 3) break;
            if (!batch.isEmpty() && batchChars + sub.length() + 1 > SEARCH_QUERY_MAX_CHARS) {
                queries++;
                searchInto(batch, offset, perQuery, out);
                batch = new ArrayList<>();
                batchChars = 0;
            }
            batch.add(sub);
            batchChars += sub.length() + 1;
            if (batch.size() >= 8) {
                queries++;
                searchInto(batch, offset, perQuery, out);
                batch = new ArrayList<>();
                batchChars = 0;
            }
        }
        if (!batch.isEmpty() && queries < 3) searchInto(batch, offset, perQuery, out);
        return new ArrayList<>(out);
    }

    private void searchInto(List<String> categories, int offset, int limit, java.util.Set<String> out) {
        try {
            out.addAll(fetchMostLinkedTitles(categories, offset, limit));
        } catch (Exception ignored) {
            // 1 バッチ失敗しても他のバッチで続ける
        }
    }

    /** 指定カテゴリ直下のサブカテゴリ名 ("Category:" 接頭辞なし) を最大 100 件。 */
    public List<String> fetchSubcategories(String category) {
        List<String> articles = new ArrayList<>();
        List<String> subcats = new ArrayList<>();
        fetchMembersInto(category, 100, null, articles, subcats);
        List<String> out = new ArrayList<>();
        for (String s : subcats) {
            out.add(s.startsWith("Category:") ? s.substring("Category:".length()) : s);
        }
        return out;
    }

    /** 抽出した年とその意味 (生年・没年・設立年など)。 */
    private record ExtractedYearInfo(int year, String kind) {}

    /** 目次 (section list) 取得。parse API の prop=sections を使う。 */
    public List<Section> fetchSections(String title) {
        JsonNode json = wikipediaWebClient.get()
            .uri(uri -> uri.path("/w/api.php")
                .queryParam("action", "parse")
                .queryParam("format", "json")
                .queryParam("prop", "sections")
                .queryParam("redirects", 1)
                .queryParam("page", title)
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        List<Section> result = new ArrayList<>();
        if (json == null) return result;
        for (JsonNode s : json.path("parse").path("sections")) {
            String line = s.path("line").asText("");
            int level = s.path("toclevel").asInt(1);
            if (!line.isBlank()) result.add(new Section(line, level));
        }
        return result;
    }

    /**
     * インフォボックス (記事右上の構造化データ) を取得。
     * parse API で wikitext を取り、簡易パースで key=value を抜く。
     */
    public Map<String, String> fetchInfobox(String title) {
        JsonNode json = wikipediaWebClient.get()
            .uri(uri -> uri.path("/w/api.php")
                .queryParam("action", "parse")
                .queryParam("format", "json")
                .queryParam("prop", "wikitext")
                .queryParam("redirects", 1)
                .queryParam("page", title)
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        Map<String, String> result = new LinkedHashMap<>();
        if (json == null) return result;
        String wikitext = json.path("parse").path("wikitext").path("*").asText("");
        if (wikitext.isEmpty()) return result;

        // {{Infobox ...}} を緩く抽出
        int start = wikitext.indexOf("{{");
        while (start >= 0) {
            // 最初の "Infobox" もしくは日本語 "基礎情報" を含むテンプレート
            int end = findTemplateEnd(wikitext, start);
            if (end < 0) break;
            String tpl = wikitext.substring(start + 2, end);
            String head = tpl.split("\\|", 2)[0].toLowerCase();
            if (head.contains("infobox") || head.contains("基礎情報") || head.contains("基礎データ")) {
                parseTemplateParams(tpl, result);
                break;
            }
            start = wikitext.indexOf("{{", end);
        }
        return result;
    }

    public String buildPageUrl(String title) {
        return "https://ja.wikipedia.org/wiki/" + URLEncoder.encode(title, StandardCharsets.UTF_8);
    }

    /**
     * カテゴリ一覧をランダムな位置から読むためのソートキー接頭辞。
     * MediaWiki の categorymembers は常にソートキー順 (日本語版は読み仮名) で先頭から返すため、
     * 何も指定しないと五十音の先頭にある同じ記事ばかりが選ばれてしまう。
     */
    static final String[] RANDOM_SORTKEY_PREFIXES = (
        "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわ"
        + "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    ).codePoints().mapToObj(Character::toString).toArray(String[]::new);

    private final java.util.Random random = new java.util.Random();

    /**
     * 指定カテゴリに属する記事タイトルを最大 limit 件取得する。
     * ランダムなソートキー位置から読み始めることで、呼ぶたびに違う記事が候補になる。
     * 記事メンバーが少ない場合は 1 段だけサブカテゴリを掘って補充する。
     */
    public List<String> fetchCategoryMembers(String category, int limit) {
        List<String> articles = new ArrayList<>();
        List<String> subcats = new ArrayList<>();

        // ランダム接頭辞で 2 回まで試し、それでも足りなければ先頭から読む
        for (int attempt = 0; attempt < 2 && articles.size() < limit; attempt++) {
            String prefix = RANDOM_SORTKEY_PREFIXES[random.nextInt(RANDOM_SORTKEY_PREFIXES.length)];
            fetchMembersInto(category, limit, prefix, articles, subcats);
        }
        if (articles.size() < limit) {
            fetchMembersInto(category, limit, null, articles, subcats);
        }
        dedupe(articles);
        dedupe(subcats);
        if (articles.size() >= limit) {
            Collections.shuffle(articles);
            return new ArrayList<>(articles.subList(0, limit));
        }

        // 不足分をサブカテゴリから補充 (1 段のみ)
        Collections.shuffle(subcats);
        for (String sub : subcats) {
            if (articles.size() >= limit) break;
            List<String> subArticles = new ArrayList<>();
            List<String> dummySub = new ArrayList<>();
            try {
                String prefix = RANDOM_SORTKEY_PREFIXES[random.nextInt(RANDOM_SORTKEY_PREFIXES.length)];
                fetchMembersInto(sub, limit - articles.size(), prefix, subArticles, dummySub);
                if (subArticles.isEmpty()) {
                    fetchMembersInto(sub, limit - articles.size(), null, subArticles, dummySub);
                }
                articles.addAll(subArticles);
            } catch (Exception ignored) {
                // 次のサブカテゴリで再試行
            }
        }
        dedupe(articles);
        Collections.shuffle(articles);
        return new ArrayList<>(articles.subList(0, Math.min(limit, articles.size())));
    }

    private static void dedupe(List<String> list) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(list);
        list.clear();
        list.addAll(set);
    }

    /** カテゴリの直接メンバー (記事 + サブカテゴリ) を 1 度の API 呼び出しで取得。 */
    private void fetchMembersInto(
        String category, int limit, String sortkeyPrefix, List<String> articles, List<String> subcats
    ) {
        String catTitle = category.startsWith("Category:") ? category : "Category:" + category;
        JsonNode json = wikipediaWebClient.get()
            .uri(uri -> {
                var b = uri.path("/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("format", "json")
                    .queryParam("list", "categorymembers")
                    .queryParam("cmtitle", catTitle)
                    .queryParam("cmtype", "page|subcat")
                    .queryParam("cmlimit", Math.min(Math.max(limit, 20), 100));
                if (sortkeyPrefix != null) b.queryParam("cmstartsortkeyprefix", sortkeyPrefix);
                return b.build();
            })
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        if (json == null) return;
        for (JsonNode m : json.path("query").path("categorymembers")) {
            int ns = m.path("ns").asInt(0);
            String t = m.path("title").asText("");
            if (t.isEmpty()) continue;
            if (ns == 14) subcats.add(t);
            else if (ns == 0) articles.add(t);
        }
    }

    // ----- 内部ヘルパー -----

    /**
     * prop=redirects の結果から、この記事の別名として使えるリダイレクト名を抽出する。
     * 「○○ (曖昧さ回避)」のような括弧付き、タイトルと同じもの、1 文字のものは除外する。
     */
    private List<String> extractAliases(JsonNode page, String title) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        seen.add(title);
        for (JsonNode r : page.path("redirects")) {
            String t = r.path("title").asText("");
            if (t.isBlank() || seen.contains(t)) continue;
            // 「タイトル (曖昧さ回避語)」形式のリダイレクトは括弧を落とした形で採用
            String core = t.replaceAll("[（(][^（()）]*[）)]", "").trim();
            if (core.isBlank() || core.codePointCount(0, core.length()) < 2) continue;
            if (seen.add(core)) out.add(core);
            if (out.size() >= 30) break;
        }
        return out;
    }

    private JsonNode firstPage(JsonNode bulk) {
        JsonNode pages = bulk.path("query").path("pages");
        if (!pages.isObject()) return null;
        var it = pages.fields();
        return it.hasNext() ? it.next().getValue() : null;
    }

    private String extractIntro(String fullExtract) {
        if (fullExtract == null || fullExtract.isEmpty()) return "";
        // 最初の節 (== 見出し == より前) を冒頭とみなす
        int idx = fullExtract.indexOf("\n\n\n");
        if (idx > 0 && idx < 1500) return fullExtract.substring(0, idx).trim();
        if (fullExtract.length() <= 1500) return fullExtract.trim();
        return fullExtract.substring(0, 1500).trim();
    }

    private List<String> fetchImageUrls(List<String> imageNames) {
        if (imageNames.isEmpty()) return List.of();
        // imageinfo API でサムネ URL を解決
        String titles = String.join("|", imageNames.subList(0, Math.min(imageNames.size(), 10)));
        JsonNode json = wikipediaWebClient.get()
            .uri(uri -> uri.path("/w/api.php")
                .queryParam("action", "query")
                .queryParam("format", "json")
                .queryParam("prop", "imageinfo")
                .queryParam("iiprop", "url")
                .queryParam("iiurlwidth", 400)
                .queryParam("titles", titles)
                .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        List<String> urls = new ArrayList<>();
        if (json == null) return urls;
        JsonNode pages = json.path("query").path("pages");
        for (JsonNode page : pages) {
            JsonNode info = page.path("imageinfo");
            if (info.isArray() && !info.isEmpty()) {
                String url = info.get(0).path("thumburl").asText(info.get(0).path("url").asText(""));
                if (!url.isEmpty()) urls.add(url);
            }
        }
        return urls;
    }

    private int findTemplateEnd(String text, int start) {
        int depth = 0;
        for (int i = start; i < text.length() - 1; i++) {
            if (text.charAt(i) == '{' && text.charAt(i + 1) == '{') {
                depth++;
                i++;
            } else if (text.charAt(i) == '}' && text.charAt(i + 1) == '}') {
                depth--;
                i++;
                if (depth == 0) return i - 1;
            }
        }
        return -1;
    }

    private void parseTemplateParams(String tpl, Map<String, String> out) {
        // ネストを保ったままトップレベルの | で分割する
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < tpl.length(); i++) {
            char c = tpl.charAt(i);
            if (c == '{' && i + 1 < tpl.length() && tpl.charAt(i + 1) == '{') { depth++; cur.append("{{"); i++; }
            else if (c == '}' && i + 1 < tpl.length() && tpl.charAt(i + 1) == '}') { depth--; cur.append("}}"); i++; }
            else if (c == '[' && i + 1 < tpl.length() && tpl.charAt(i + 1) == '[') { depth++; cur.append("[["); i++; }
            else if (c == ']' && i + 1 < tpl.length() && tpl.charAt(i + 1) == ']') { depth--; cur.append("]]"); i++; }
            else if (c == '|' && depth == 0) { parts.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        if (cur.length() > 0) parts.add(cur.toString());

        // parts[0] はテンプレート名なのでスキップ
        for (int i = 1; i < parts.size(); i++) {
            String p = parts.get(i);
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            String key = p.substring(0, eq).trim();
            String value = p.substring(eq + 1).trim();
            value = stripWikiMarkup(value);
            if (!key.isEmpty() && !value.isEmpty()) out.put(key, value);
        }
    }

    private String stripWikiMarkup(String s) {
        // [[link|display]] → display, [[link]] → link
        s = s.replaceAll("\\[\\[([^\\]\\|]+)\\|([^\\]]+)\\]\\]", "$2");
        s = s.replaceAll("\\[\\[([^\\]]+)\\]\\]", "$1");
        // '''bold''' / ''italic'' を剥がす
        s = s.replaceAll("'{2,5}", "");
        // <ref ...>...</ref> を除去
        s = s.replaceAll("<ref[^>]*>.*?</ref>", "");
        s = s.replaceAll("<ref[^/]*/>", "");
        // <br /> 系
        s = s.replaceAll("<br\\s*/?>", " ");
        return s.trim();
    }

    /**
     * インフォボックスから年とその種類を取り出す。
     * 種類が判明しない記事は出題に向かないため null を返す (本文冒頭からのフォールバックは廃止)。
     */
    private ExtractedYearInfo extractYearInfo(Map<String, String> infobox, String intro) {
        for (Map.Entry<String, String> e : infobox.entrySet()) {
            String key = e.getKey().toLowerCase();
            String kind = classifyYearKey(key);
            if (kind == null) continue;
            // 値内から妥当な範囲 (1-2100) の数字を最初に出てきた順に検索
            Matcher m = ANY_YEAR_NUMBER.matcher(e.getValue());
            while (m.find()) {
                int y = Integer.parseInt(m.group(1));
                if (y >= 1 && y <= 2100) {
                    return new ExtractedYearInfo(y, kind);
                }
            }
        }
        return null;
    }

    /** インフォボックスのキー名から年の種類を分類する。 */
    private String classifyYearKey(String key) {
        if (key.contains("生年") || key.contains("生誕") || key.contains("birth_date") || key.contains("birthdate")) return "birth";
        if (key.contains("死没") || key.contains("没年") || key.contains("死去") || key.contains("death_date") || key.contains("deathdate")) return "death";
        if (key.contains("設立")) return "founded";
        if (key.contains("成立")) return "established";
        if (key.contains("開業")) return "opened";
        if (key.contains("開園") || key.contains("開館") || key.contains("開校") || key.contains("開店")) return "opened";
        if (key.contains("竣工") || key.contains("完成") || key.contains("落成")) return "completed";
        if (key.contains("発表") || key.contains("発売") || key.contains("公開") || key.contains("初演") || key.contains("放送開始")) return "released";
        if (key.contains("発生") || key.contains("勃発")) return "occurred";
        if (key.contains("制定") || key.contains("制作") || key.contains("作詞") || key.contains("作曲")) return "released";
        if (key.contains("結成") || key.contains("創立") || key.contains("創業")) return "founded";
        return null;
    }
}
