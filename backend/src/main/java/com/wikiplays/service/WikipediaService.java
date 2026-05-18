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
                .queryParam("prop", "extracts|images|categories|langlinks|info")
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

        List<Section> sections = fetchSections(resolvedTitle);
        Map<String, String> infobox = fetchInfobox(resolvedTitle);
        Integer pageViews = fetchRecentPageViews(resolvedTitle);
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
            pageViews,
            articleLength,
            buildPageUrl(resolvedTitle),
            yearInfo == null ? null : yearInfo.year,
            yearInfo == null ? null : yearInfo.kind
        );
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

    /** Wikipedia の Pageviews REST API で直近 30 日の閲覧数合計を取得。 */
    public Integer fetchRecentPageViews(String title) {
        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            String end = today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            String start = today.minusDays(30).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            String encoded = URLEncoder.encode(title, StandardCharsets.UTF_8);

            JsonNode json = wikipediaWebClient.get()
                .uri("https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/ja.wikipedia/all-access/all-agents/"
                    + encoded + "/daily/" + start + "/" + end)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            if (json == null) return null;
            int total = 0;
            for (JsonNode item : json.path("items")) total += item.path("views").asInt(0);
            return total;
        } catch (Exception e) {
            return null; // 取れなければ無視
        }
    }

    public String buildPageUrl(String title) {
        return "https://ja.wikipedia.org/wiki/" + URLEncoder.encode(title, StandardCharsets.UTF_8);
    }

    /**
     * 指定カテゴリに属する記事タイトルを最大 limit 件取得する。
     * 記事メンバーが少ない場合は 1 段だけサブカテゴリを掘って補充する。
     */
    public List<String> fetchCategoryMembers(String category, int limit) {
        List<String> articles = new ArrayList<>();
        List<String> subcats = new ArrayList<>();
        fetchMembersInto(category, limit, articles, subcats);

        if (articles.size() >= limit) return articles.subList(0, Math.min(limit, articles.size()));

        // 不足分をサブカテゴリから補充 (1 段のみ)
        Collections.shuffle(subcats);
        for (String sub : subcats) {
            if (articles.size() >= limit) break;
            List<String> subArticles = new ArrayList<>();
            List<String> dummySub = new ArrayList<>();
            try {
                fetchMembersInto(sub, limit - articles.size(), subArticles, dummySub);
                articles.addAll(subArticles);
            } catch (Exception ignored) {
                // 次のサブカテゴリで再試行
            }
        }
        return articles.subList(0, Math.min(limit, articles.size()));
    }

    /** カテゴリの直接メンバー (記事 + サブカテゴリ) を 1 度の API 呼び出しで取得。 */
    private void fetchMembersInto(String category, int limit, List<String> articles, List<String> subcats) {
        String catTitle = category.startsWith("Category:") ? category : "Category:" + category;
        JsonNode json = wikipediaWebClient.get()
            .uri(uri -> uri.path("/w/api.php")
                .queryParam("action", "query")
                .queryParam("format", "json")
                .queryParam("list", "categorymembers")
                .queryParam("cmtitle", catTitle)
                .queryParam("cmtype", "page|subcat")
                .queryParam("cmlimit", Math.min(Math.max(limit, 20), 100))
                .build())
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

    /** 記事タイトル・別名を本文からマスクする (モード A/B などで使う)。 */
    public String maskTitle(String content, String title) {
        if (content == null || content.isBlank()) return content;
        List<String> targets = new ArrayList<>();
        targets.add(title);
        int paren = title.indexOf('(');
        if (paren > 0) targets.add(title.substring(0, paren).trim());
        int parenJa = title.indexOf('（');
        if (parenJa > 0) targets.add(title.substring(0, parenJa).trim());

        String masked = content;
        for (String t : targets) {
            if (t == null || t.isBlank()) continue;
            masked = masked.replace(t, "????");
        }
        return masked;
    }

    // ----- 内部ヘルパー -----

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
