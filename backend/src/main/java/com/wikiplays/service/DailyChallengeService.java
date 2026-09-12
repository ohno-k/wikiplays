package com.wikiplays.service;

import com.wikiplays.dto.ArticleData;
import com.wikiplays.dto.DailyChallengeResponse;
import com.wikiplays.dto.DailyLeaderboardEntry;
import com.wikiplays.entity.DailyChallenge;
import com.wikiplays.entity.DailyScore;
import com.wikiplays.repository.DailyChallengeRepository;
import com.wikiplays.repository.DailyScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * デイリーチャレンジの生成・取得・スコア記録・ランキングを管理。
 * 日付は Asia/Tokyo の日付境界を採用。
 */
@Service
public class DailyChallengeService {

    private static final Logger log = LoggerFactory.getLogger(DailyChallengeService.class);
    private static final ZoneId TZ = ZoneId.of("Asia/Tokyo");
    private static final int QUESTIONS_PER_DAY = 5;

    private final DailyChallengeRepository challengeRepo;
    private final DailyScoreRepository scoreRepo;
    private final ArticlePoolService articlePool;

    public DailyChallengeService(
        DailyChallengeRepository challengeRepo,
        DailyScoreRepository scoreRepo,
        ArticlePoolService articlePool
    ) {
        this.challengeRepo = challengeRepo;
        this.scoreRepo = scoreRepo;
        this.articlePool = articlePool;
    }

    public LocalDate today() {
        return LocalDate.now(TZ);
    }

    /** 今日の (scope, genre) チャレンジのエンティティを取得 (なければ生成)。 */
    @Transactional
    public Optional<DailyChallenge> getOrCreateToday(String scope, String genre) {
        LocalDate today = LocalDate.now(TZ);
        String scopeKey = nullToEmpty(normalize(scope));
        String genreKey = nullToEmpty(normalize(genre));
        DailyChallenge challenge = challengeRepo
            .findByDateAndScopeKeyAndGenreKey(today, scopeKey, genreKey)
            .orElseGet(() -> generateNew(today, scopeKey, genreKey));
        return Optional.ofNullable(challenge);
    }

    /** 今日のデイリーチャレンジの概要を取得 (なければ作成)。記事本文は含めない。
     *  playerId が指定されていれば、その人の今日のスコア (既プレイなら) を myScore に含める。 */
    @Transactional
    public Optional<DailyChallengeResponse> getToday(String scope, String genre, String playerId) {
        return getOrCreateToday(scope, genre).map(c -> toResponse(c, playerId));
    }

    /** チャレンジ ID から概要を取得 (アーカイブ用)。 */
    @Transactional
    public Optional<DailyChallengeResponse> getById(Long id, String playerId) {
        return challengeRepo.findById(id).map(c -> toResponse(c, playerId));
    }

    /** チャレンジの出題記事 (プールから復元)。 */
    @Transactional
    public List<ArticleData> articlesOf(DailyChallenge challenge) {
        List<ArticleData> articles = new ArrayList<>();
        for (String t : splitCsv(challenge.getArticleTitlesCsv())) {
            articlePool.findByTitle(t).ifPresent(articles::add);
        }
        return articles;
    }

    private DailyChallenge generateNew(LocalDate date, String scopeKey, String genreKey) {
        String scope = scopeKey.isEmpty() ? null : scopeKey;
        String genre = genreKey.isEmpty() ? null : genreKey;
        List<ArticleData> sample = articlePool.getRandomSample(scope, genre, QUESTIONS_PER_DAY);
        if (sample.size() < QUESTIONS_PER_DAY) {
            // プールに足りない場合は補充を試みる
            int needed = QUESTIONS_PER_DAY - sample.size();
            for (int i = 0; i < needed; i++) {
                articlePool.getRandom(scope, genre).ifPresent(sample::add);
            }
        }
        if (sample.size() < QUESTIONS_PER_DAY) {
            log.warn("not enough articles for daily challenge {} {}/{}", date, scopeKey, genreKey);
            return null;
        }

        DailyChallenge c = new DailyChallenge();
        c.setDate(date);
        c.setScopeKey(scopeKey);
        c.setGenreKey(genreKey);
        c.setArticleTitlesCsv(joinCsv(sample.stream().map(ArticleData::title).toList()));
        c.setCreatedAt(Instant.now());
        try {
            return challengeRepo.save(c);
        } catch (Exception e) {
            // 同時生成競合 → 既に作られたものを取得
            return challengeRepo.findByDateAndScopeKeyAndGenreKey(date, scopeKey, genreKey).orElse(null);
        }
    }

    private DailyChallengeResponse toResponse(DailyChallenge challenge, String playerId) {
        int questionCount = splitCsv(challenge.getArticleTitlesCsv()).size();
        long playerCount = scoreRepo.countByDailyChallengeId(challenge.getId());
        int top = scoreRepo
            .findByDailyChallengeIdOrderByScoreDescPlayedAtAsc(challenge.getId(), PageRequest.of(0, 1))
            .stream().mapToInt(DailyScore::getScore).max().orElse(0);
        Integer myScore = null;
        if (playerId != null && !playerId.isBlank()) {
            myScore = scoreRepo
                .findFirstByDailyChallengeIdAndPlayerId(challenge.getId(), playerId)
                .map(DailyScore::getScore)
                .orElse(null);
        }
        return new DailyChallengeResponse(
            challenge.getId(),
            challenge.getDate(),
            challenge.getScopeKey().isEmpty() ? null : challenge.getScopeKey(),
            challenge.getGenreKey().isEmpty() ? null : challenge.getGenreKey(),
            questionCount,
            playerCount,
            top,
            myScore
        );
    }

    /** 既にこのチャレンジをプレイ済みか。 */
    public boolean hasPlayed(Long challengeId, String playerId) {
        return scoreRepo.findFirstByDailyChallengeIdAndPlayerId(challengeId, playerId).isPresent();
    }

    /**
     * サーバーで採点したスコアを記録する (同じ playerId は 1 チャレンジ 1 回まで)。
     * クライアントからの直接提出は受け付けない (GameSessionService 経由のみ)。
     */
    @Transactional
    public boolean recordScore(Long challengeId, String playerId, String displayName, int score) {
        if (challengeId == null || playerId == null || playerId.isBlank()) return false;
        if (hasPlayed(challengeId, playerId)) return false;
        DailyScore ds = new DailyScore();
        ds.setDailyChallengeId(challengeId);
        ds.setPlayerId(playerId);
        String name = displayName;
        if (name == null || name.isBlank()) name = "名無し";
        if (name.length() > 32) name = name.substring(0, 32);
        ds.setDisplayName(name);
        ds.setScore(Math.max(0, Math.min(score, 5000)));
        ds.setPlayedAt(Instant.now());
        scoreRepo.save(ds);
        return true;
    }

    public List<DailyLeaderboardEntry> leaderboard(Long challengeId, int limit) {
        List<DailyScore> scores = scoreRepo.findByDailyChallengeIdOrderByScoreDescPlayedAtAsc(
            challengeId, PageRequest.of(0, Math.min(limit, 100))
        );
        List<DailyLeaderboardEntry> out = new ArrayList<>();
        int rank = 1;
        for (DailyScore s : scores) {
            out.add(new DailyLeaderboardEntry(s.getDisplayName(), s.getScore(), s.getPlayedAt(), rank++));
        }
        return out;
    }

    private String normalize(String s) {
        if (s == null || s.isBlank() || "random".equals(s)) return null;
        return s;
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }

    private String joinCsv(List<String> titles) {
        return String.join("\t", titles);
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return List.of(csv.split("\t"));
    }
}
