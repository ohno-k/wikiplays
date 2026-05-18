package com.wikiplays.dto;

import java.util.List;
import java.util.Map;

/**
 * Wikipedia 記事の全データレイヤーをまとめた DTO。
 * 各ゲームモードはこの中から必要な情報を選んで使う。
 */
public record ArticleData(
    String title,
    String introExtract,           // 冒頭 (リード) のプレーンテキスト
    String fullExtract,            // 記事全文のプレーンテキスト
    List<Section> sections,        // 目次 (節構造)
    List<String> imageUrls,        // 記事内画像 (サムネ URL)
    Map<String, String> infobox,   // インフォボックス key→value
    List<String> categories,       // 所属カテゴリ
    int languageLinkCount,         // 他言語版の数
    Integer recentPageViews,       // 直近の閲覧数 (取れない場合 null)
    int articleLength,             // 記事のバイト長
    String pageUrl,                // Wikipedia の記事 URL
    Integer extractedYear,         // 主題に紐づく年 (取れない場合 null) — モード C 用
    String extractedYearKind       // 年の種類 (birth/death/founded/...) — モード C で表示用
) {}
