package com.wikiplays.service;

import com.wikiplays.dto.ArticleData;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 出題対象として安全な記事かを判定するフィルタ。
 *
 * 倫理的に避けるべき記事 (被害者がいる事件、現代の悲劇、自殺、災害の個別犠牲者など)
 * を多段のルールで除外する。完全自動化は危険なので、最初は厳しめに弾く。
 *
 * 存命人物は以前は一律除外していたが、スポーツ・音楽・IT などの現役がほぼ全滅して
 * ジャンルの面白さを損なうため、事件系の語が本文にあるものだけ除外する方針に変更した。
 */
@Service
public class ArticleFilter {

    /** 記事を除外する根拠キーワード (カテゴリ・タイトルに含まれていたら除外)。 */
    private static final Set<String> CATEGORY_BLOCKLIST = Set.of(
        "事件", "殺人", "殺害", "殺傷", "テロ", "暴行", "強姦", "強制わいせつ",
        "誘拐", "監禁", "傷害", "虐殺", "虐待", "犯罪", "犯人", "受刑者", "死刑囚",
        "詐欺", "横領", "汚職", "不祥事",
        "自殺", "自死", "心中", "失踪",
        "事故", "災害", "墜落", "脱線", "沈没", "炎上", "爆発",
        "感染症", "疫病", "病死", "急死",
        "ポルノ", "アダルト", "性風俗",
        "陰謀論", "差別"
    );

    /** 記事本文に頻出していたら除外する語。1 回でも出るとアウトな強キーワード。 */
    private static final Set<String> CONTENT_HARD_BLOCKLIST = Set.of(
        "殺害された", "殺害事件", "殺人事件", "通り魔",
        "強姦", "強制性交", "わいせつ事件",
        "自殺した", "自死した", "心中事件",
        "テロ事件", "爆弾テロ", "無差別殺傷",
        "拉致事件", "誘拐事件"
    );

    /** 記事本文に多数回出ると除外候補にする語。 */
    private static final Set<String> CONTENT_SOFT_BLOCKLIST = Set.of(
        "事件", "事故", "被害者", "遺族", "容疑者", "逮捕", "起訴"
    );

    /** 記事タイトル末尾が「事件」「事故」「災害」などで終わるものは除外。 */
    private static final List<String> TITLE_SUFFIX_BLOCKLIST = List.of(
        "事件", "事故", "災害", "戦争", "紛争", "テロ", "暴動", "騒動",
        "虐殺", "暗殺", "失踪", "自殺", "墜落", "脱線", "沈没"
    );

    /** 記事タイトルが含むと出題に不向きなパターン (列挙・索引・年表など)。 */
    private static final List<String> TITLE_CONTAINS_BLOCKLIST = List.of(
        "の一覧", "一覧表", "の年表", "年表)", "の索引",
        "曖昧さ回避", "リダイレクト",
        "の歴史", "の沿革", "の年代記",
        "プロジェクト", "Portal:", "Wikipedia:"
    );

    /** タイトルの末尾が指定語で終わる場合は出題不向き。 */
    private static final List<String> TITLE_ADDITIONAL_SUFFIX_BLOCKLIST = List.of(
        "一覧",        // 「○○一覧」「○○の一覧」を全て弾く
        "リスト",
        "協会", "学会", "委員会", "連盟", "連合会",
        "の作品",      // 「○○の作品」のような索引記事
        "の家系図", "の系図",
        "の関係者", "の出身者", "の人物", "の登場人物",
        "総合"
    );

    /**
     * 存命人物は出題対象に含めるが、事件・スキャンダル系の語が本文に少しでも多ければ弾く。
     * (故人・非人物記事は 5 回、存命人物は 2 回でアウト)
     */
    private static final int SOFT_LIMIT_DEFAULT = 5;
    private static final int SOFT_LIMIT_LIVING = 2;

    public boolean isAllowed(ArticleData article) {
        if (article == null) return false;
        boolean living = false;
        if (article.categories() != null) {
            for (String cat : article.categories()) {
                if (cat.contains("存命人物")) { living = true; break; }
            }
        }

        // 記事タイトルそのものが事件・事故系
        String title = article.title();
        if (title != null) {
            for (String suffix : TITLE_SUFFIX_BLOCKLIST) {
                if (title.endsWith(suffix)) return false;
            }
            for (String suffix : TITLE_ADDITIONAL_SUFFIX_BLOCKLIST) {
                if (title.endsWith(suffix)) return false;
            }
            for (String pattern : TITLE_CONTAINS_BLOCKLIST) {
                if (title.contains(pattern)) return false;
            }
            // タイトルが「○○のページ」「○○のリスト」など
            if (title.endsWith("のページ") || title.endsWith("のリスト")) return false;
        }

        // カテゴリにブロックワードが含まれる
        for (String cat : article.categories()) {
            for (String bw : CATEGORY_BLOCKLIST) {
                if (cat.contains(bw)) return false;
            }
        }

        // 本文の強キーワード (1 回でも出たらアウト)
        String full = nullSafe(article.fullExtract());
        for (String bw : CONTENT_HARD_BLOCKLIST) {
            if (full.contains(bw)) return false;
        }

        // 本文の弱キーワード (合計 N 回以上でアウト。存命人物はより厳しく)
        int softLimit = living ? SOFT_LIMIT_LIVING : SOFT_LIMIT_DEFAULT;
        int softHits = 0;
        for (String bw : CONTENT_SOFT_BLOCKLIST) {
            int idx = 0;
            while ((idx = full.indexOf(bw, idx)) >= 0) {
                softHits++;
                idx += bw.length();
                if (softHits >= softLimit) return false;
            }
        }

        // 本文・冒頭が短すぎる記事はゲーム素材として不向き
        if (nullSafe(article.introExtract()).length() < 80) return false;

        return true;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
