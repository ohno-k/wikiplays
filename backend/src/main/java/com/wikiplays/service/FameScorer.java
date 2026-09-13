package com.wikiplays.service;

import com.wikiplays.dto.ArticleData;

import java.util.ArrayList;
import java.util.List;

/**
 * 記事の主題の「知名度」を 1 つの数値にまとめ、絶対基準で 5 段階 (fame tier) に分ける。
 *
 * スコアの単位は「1 日あたり閲覧数の log10」。
 *   - 閲覧数が取れている記事 (recentPageViews) はそれをそのまま使う。
 *     日本語版 Wikipedia の閲覧数は日本のプレイヤーにとっての知名度をほぼそのまま表す
 *     (誰でも知っている題材は 1 日 1000 回以上、無名の題材は 1 日 10 回程度)。
 *   - 取れていない旧キャッシュは、他言語版数・記事バイト長・別名数から同じ単位に推定する
 *     (取得を再開した起動時バックフィルで順次実測値に置き換わる)。
 *
 * tier はバケット内の相対順位ではなく絶対値で決める。
 * ランダム取得した記事の大半は無名なので、相対順位の「上位 20%」では常識レベルにならない。
 * 常識レベル (tier 1) の記事は {@link ArticlePoolService} が別途「被リンク数の多い記事」を取りに行って補充する。
 */
public final class FameScorer {

    private FameScorer() {}

    /** 知名度の段階数。1 = 常識レベル、TIERS = 超マニアック。 */
    public static final int TIERS = 5;

    /** 「常識レベル」とみなす tier の上限 (この tier 以下を要求されたときは専用の補充を行う)。 */
    public static final int FAMOUS_TIER_MAX = 2;

    /**
     * 各 tier に入るための 1 日あたり閲覧数の下限 (tier 1〜4。tier 5 は残り全部)。
     * 目安: 富士山・徳川家康・山手線 ≈ 1500〜4000/日、主要駅・有名企業 ≈ 300〜1000/日、
     *       地方の駅・中堅の人物 ≈ 50〜200/日、無名の題材 ≈ 10/日以下。
     */
    static final int[] TIER_MIN_DAILY_VIEWS = {700, 250, 80, 25};

    /** 旧キャッシュ用の推定式の係数 (単位は log10 閲覧数/日)。 */
    static final double EST_BASE = 0.6;
    static final double EST_WEIGHT_LANGLINKS = 0.9;
    static final double EST_WEIGHT_LENGTH = 0.35;
    static final double EST_WEIGHT_ALIASES = 0.3;

    /** 知名度スコア。大きいほど有名。単位は log10(1 + 1 日あたり閲覧数)。 */
    public static double score(ArticleData a) {
        if (a == null) return 0.0;
        Integer views = a.recentPageViews();
        if (views != null && views >= 0) return viewsToScore(views);
        return estimateFromMetadata(a);
    }

    /** 1 日あたり閲覧数 → スコア。 */
    public static double viewsToScore(double dailyViews) {
        return Math.log10(1.0 + Math.max(0.0, dailyViews));
    }

    /** 閲覧数が無い記事のための推定。桁がばらつく各指標を log10 で圧縮して重み付き和にする。 */
    static double estimateFromMetadata(ArticleData a) {
        double langlinks = Math.log10(1.0 + Math.max(0, a.languageLinkCount()));
        double length = Math.log10(1.0 + Math.max(0, a.articleLength()) / 1000.0);
        double aliases = Math.log10(1.0 + a.aliases().size());
        return EST_BASE
            + EST_WEIGHT_LANGLINKS * langlinks
            + EST_WEIGHT_LENGTH * length
            + EST_WEIGHT_ALIASES * aliases;
    }

    /** スコアが属する tier (1〜TIERS)。 */
    public static int tierOf(double score) {
        for (int t = 1; t < TIERS; t++) {
            if (score >= minScore(t)) return t;
        }
        return TIERS;
    }

    /** tier に入るためのスコア下限 (含む)。tier 5 は下限なし。 */
    public static double minScore(int tier) {
        if (tier >= TIERS) return -Double.MAX_VALUE;
        return viewsToScore(TIER_MIN_DAILY_VIEWS[tier - 1]);
    }

    /** tier のスコア上限 (含まない)。tier 1 は上限なし。 */
    public static double maxScoreExclusive(int tier) {
        if (tier <= 1) return Double.MAX_VALUE;
        return minScore(tier - 1);
    }

    /** クライアントから受け取った tier を検証する。範囲外・未指定は null (指定なし)。 */
    public static Integer normalizeTier(Integer tier) {
        if (tier == null || tier < 1 || tier > TIERS) return null;
        return tier;
    }

    /**
     * 指定 tier に記事が足りないときに代わりに使う tier の順番。
     * 近い tier を優先し、同じ距離なら簡単な方 (番号が小さい方) を先にする。
     * 例: tier 3 → [3, 2, 4, 1, 5]、tier 1 → [1, 2, 3, 4, 5]
     */
    public static List<Integer> fallbackOrder(int tier) {
        List<Integer> out = new ArrayList<>();
        out.add(tier);
        for (int d = 1; d < TIERS; d++) {
            if (tier - d >= 1) out.add(tier - d);
            if (tier + d <= TIERS) out.add(tier + d);
        }
        return out;
    }
}
