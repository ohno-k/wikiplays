package com.wikiplays.service;

import com.wikiplays.dto.ArticleData;

/**
 * 記事の主題の「知名度」を 1 つの数値にまとめる。
 *
 * 出題時はこのスコアを (scope, genre) バケット内で順位づけし、5 段階 (fame tier) に分ける。
 * 絶対値そのものに意味はなく、同じバケット内での大小関係だけを使う。
 *
 * 使うシグナルは記事取得時にすでに保存されているものだけ (追加の API 呼び出しなし):
 *  - 他言語版の数        : 世界的な知名度。日本ローカルな主題でも「有名なら数言語はある」
 *  - 記事のバイト長      : 有名な主題ほど加筆されて長い (分野による偏りはバケット内順位で吸収)
 *  - リダイレクト別名の数 : 略称・旧表記が多い = よく言及される
 *
 * いずれも桁がばらつくので log で圧縮してから重み付き和にする。
 * ページビューは現在取得していないため使わない (取得を再開した場合は全記事を再計算すること。
 * 一部の記事だけに項が乗ると同一バケット内の比較が壊れる)。
 */
public final class FameScorer {

    private FameScorer() {}

    /** 知名度の段階数。1 = 最も有名、TIERS = 最もマニアック。 */
    public static final int TIERS = 5;

    static final double WEIGHT_LANGLINKS = 1.0;
    static final double WEIGHT_LENGTH = 0.7;
    static final double WEIGHT_ALIASES = 0.5;

    public static double score(ArticleData a) {
        if (a == null) return 0.0;
        double langlinks = Math.log1p(Math.max(0, a.languageLinkCount()));
        double length = Math.log1p(Math.max(0, a.articleLength()) / 1000.0);
        double aliases = Math.log1p(a.aliases().size());
        return WEIGHT_LANGLINKS * langlinks + WEIGHT_LENGTH * length + WEIGHT_ALIASES * aliases;
    }

    /** クライアントから受け取った tier を検証する。範囲外・未指定は null (指定なし)。 */
    public static Integer normalizeTier(Integer tier) {
        if (tier == null || tier < 1 || tier > TIERS) return null;
        return tier;
    }
}
