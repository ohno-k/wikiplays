package com.wikiplays.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (scope, genre) → 対応する Wikipedia カテゴリのマップ。
 * カテゴリは "Category:" 接頭辞なしで指定する。
 *
 * 倫理ガイドラインに沿った安全なカテゴリのみを採用している。
 * 戦争・事件・現代の悲劇に関するカテゴリは入れない。
 */
public final class GenreCatalog {

    private GenreCatalog() {}

    private static final Map<String, List<String>> GENRES = buildGenres();

    private static Map<String, List<String>> buildGenres() {
        Map<String, List<String>> m = new HashMap<>();

        // === 鉄道 ===
        m.put("jp:rail", List.of(
            "日本の鉄道路線", "JRの鉄道駅", "私鉄の鉄道駅", "日本の電車", "日本の鉄道事業者"
        ));
        m.put("world:rail", List.of(
            "アメリカ合衆国の鉄道路線", "ドイツの鉄道", "イギリスの鉄道", "フランスの鉄道",
            "中華人民共和国の鉄道路線", "韓国の鉄道路線"
        ));

        // === 歴史・人物 ===
        m.put("jp:history", List.of(
            "日本の城", "戦国大名", "江戸時代の大名", "鎌倉幕府の御家人", "室町幕府の管領"
        ));
        m.put("world:history", List.of(
            "古代ローマの人物", "中国の皇帝", "イングランドの君主", "フランスの君主", "ローマ皇帝"
        ));

        // === 地理 ===
        m.put("jp:geography", List.of(
            "日本の市", "日本の町", "日本の山", "日本の川", "日本の島", "日本の世界遺産"
        ));
        m.put("world:geography", List.of(
            "アジアの首都", "ヨーロッパの首都", "アフリカの首都", "南アメリカの首都", "世界の山"
        ));

        // === 科学 ===
        m.put("jp:science", List.of(
            "日本の物理学者", "日本の数学者", "日本の化学者", "日本の天文学者"
        ));
        m.put("world:science", List.of(
            "化学元素", "ノーベル物理学賞受賞者", "ノーベル化学賞受賞者", "数学者"
        ));

        // === 生物 ===
        m.put("jp:biology", List.of(
            "日本の哺乳類", "日本の鳥類", "日本の固有種"
        ));
        m.put("world:biology", List.of(
            "哺乳類", "鳥類", "魚類", "両生類", "爬虫類"
        ));

        // === 植物 ===
        m.put("jp:plant", List.of(
            "日本の植物", "日本の固有植物"
        ));
        m.put("world:plant", List.of(
            "被子植物", "裸子植物", "樹木", "ハーブ"
        ));

        // === 古生物 ===
        m.put("jp:paleontology", List.of(
            "日本の古生物", "日本産の恐竜"
        ));
        m.put("world:paleontology", List.of(
            "恐竜", "中生代の生物", "古生代の生物", "新生代の哺乳類"
        ));

        // === 天体 ===
        m.put("jp:astronomy", List.of(
            "日本の天文学者", "日本の天文台", "日本の人工衛星"
        ));
        m.put("world:astronomy", List.of(
            "恒星", "惑星", "衛星", "銀河", "星座", "彗星", "小惑星"
        ));

        // === 芸術 ===
        m.put("jp:art", List.of(
            "日本の画家", "日本の彫刻家", "日本の建築家", "日本の作曲家"
        ));
        m.put("world:art", List.of(
            "西洋の画家", "ルネサンス期の人物", "印象派の画家", "西洋の彫刻家"
        ));

        // === 文学 ===
        m.put("jp:literature", List.of(
            "日本の小説家", "日本の小説", "日本の詩人", "日本の随筆家"
        ));
        m.put("world:literature", List.of(
            "アメリカ合衆国の小説家", "イギリスの小説家", "フランスの小説家",
            "ドイツの小説家", "ロシアの小説家"
        ));

        // === 音楽 ===
        m.put("jp:music", List.of(
            "日本の歌手", "日本の作曲家", "日本のロック・バンド", "日本のポップ・ミュージシャン"
        ));
        m.put("world:music", List.of(
            "クラシック音楽の作曲家", "ロック・バンド", "ジャズ・ミュージシャン",
            "ポピュラー音楽のミュージシャン"
        ));

        // === 映画 ===
        m.put("jp:movie", List.of(
            "日本の映画作品", "日本の映画監督", "日本のアニメ映画"
        ));
        m.put("world:movie", List.of(
            "アメリカ合衆国の映画作品", "アカデミー作品賞受賞作品",
            "アメリカ合衆国の映画監督", "フランスの映画作品"
        ));

        // === アニメ・漫画 ===
        m.put("jp:anime", List.of(
            "日本の漫画作品", "日本のアニメ作品", "日本の漫画家",
            "コンピュータゲームの作品", "テレビアニメ"
        ));
        m.put("world:anime", List.of(
            "アメリカ合衆国のテレビアニメ", "アメリカン・コミックス", "ディズニー作品"
        ));

        // === スポーツ ===
        m.put("jp:sports", List.of(
            "日本の野球選手", "日本のサッカー選手", "大相撲力士",
            "日本のオリンピック金メダリスト", "日本の競走馬"
        ));
        m.put("world:sports", List.of(
            "サッカー選手", "野球選手", "テニス選手", "オリンピック金メダリスト",
            "バスケットボール選手"
        ));

        // === 神話 ===
        m.put("jp:mythology", List.of(
            "日本神話", "妖怪", "日本の神"
        ));
        m.put("world:mythology", List.of(
            "ギリシア神話の人物", "ローマ神話の神", "北欧神話", "エジプト神話"
        ));

        // === 建築 ===
        m.put("jp:architecture", List.of(
            "日本の建築物", "日本のタワー", "日本の橋", "日本の超高層建築物"
        ));
        m.put("world:architecture", List.of(
            "超高層建築物", "世界の橋", "世界遺産", "古代建築"
        ));

        // === 乗り物 ===
        m.put("jp:vehicle", List.of(
            "日本の自動車", "日本のオートバイ", "日本の旅客機"
        ));
        m.put("world:vehicle", List.of(
            "旅客機", "自動車のモデル", "オートバイ", "船舶"
        ));

        // === 言語 ===
        m.put("jp:language", List.of(
            "日本語", "日本の方言", "日本語の文法"
        ));
        m.put("world:language", List.of(
            "言語", "ヨーロッパの言語", "アジアの言語", "アフリカの言語"
        ));

        // === IT・コンピュータ ===
        m.put("jp:computer", List.of(
            "日本のソフトウェア会社", "日本のコンピュータゲームメーカー"
        ));
        m.put("world:computer", List.of(
            "プログラミング言語", "オペレーティングシステム", "ソフトウェア企業",
            "コンピュータ科学者"
        ));

        // === 食 ===
        m.put("jp:food", List.of(
            "和食", "日本酒", "日本の菓子", "日本の郷土料理"
        ));
        m.put("world:food", List.of(
            "各国料理", "イタリア料理", "中華料理", "フランス料理", "メキシコ料理"
        ));

        return Map.copyOf(m);
    }

    /**
     * 指定 scope, genre に対応するカテゴリ一覧を返す。
     * 未定義の組み合わせは空リスト (= 全 Wikipedia ランダムにフォールバック)。
     */
    public static List<String> getCategories(String scope, String genre) {
        if (scope == null || genre == null) return List.of();
        return GENRES.getOrDefault(scope + ":" + genre, List.of());
    }

    /** 定義済みの (scope, genre) の組。 */
    public record Bucket(String scope, String genre) {}

    /** 定義済みの全バケット (順序は固定)。総合 (scope も genre も無し) は含まない。 */
    public static List<Bucket> allBuckets() {
        List<Bucket> out = new java.util.ArrayList<>();
        for (String key : new java.util.TreeSet<>(GENRES.keySet())) {
            String[] parts = key.split(":", 2);
            out.add(new Bucket(parts[0], parts[1]));
        }
        return out;
    }
}
