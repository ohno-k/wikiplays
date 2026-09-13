package com.wikiplays.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikiplays.dto.ArticleData;
import com.wikiplays.entity.CachedArticle;
import com.wikiplays.entity.CustomGenre;
import com.wikiplays.entity.DailyChallenge;
import com.wikiplays.entity.GameSession;
import com.wikiplays.entity.PlayRecord;
import com.wikiplays.entity.User;
import com.wikiplays.repository.CachedArticleRepository;
import com.wikiplays.repository.CustomGenreRepository;
import com.wikiplays.repository.DailyChallengeRepository;
import com.wikiplays.repository.GameSessionRepository;
import com.wikiplays.repository.PlayRecordRepository;
import com.wikiplays.service.ArticleJson;
import com.wikiplays.service.ArticlePoolService;
import com.wikiplays.service.CommunityGenrePoolWarmer;
import com.wikiplays.service.DailyChallengeService;
import com.wikiplays.service.FameScorer;
import com.wikiplays.service.PlayQuotaService;
import com.wikiplays.service.XpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * A モード / デイリーのゲーム進行をサーバー側で管理する。
 *
 * - 答え・全段落・採点はサーバーだけが持つ
 * - 段落は時間経過で自動開示 (クライアントの明示要求と、経過時間から計算した数の大きい方)
 * - 最初の 1 文字を入力した時点で開示段落数を固定する (入力中は減点されない)
 * - 1 回のミスはライフで許容 (その問題のスコアは半減)、2 回目で不正解
 */
@Service
public class GameSessionService {

    private static final Logger log = LoggerFactory.getLogger(GameSessionService.class);

    public static final int TOTAL_QUESTIONS = 5;
    public static final int MAX_SCORE_PER_QUESTION = 1000;
    static final int[] SCORES_BY_REVEAL = {1000, 800, 600, 400, 200, 100};
    /** 通信遅延分の猶予。時間経過による自動開示の計算から差し引く。 */
    static final long GRACE_MS = 1500;
    private static final int MAX_ANSWER_CHARS = 20;

    private final GameSessionRepository sessionRepo;
    private final PlayRecordRepository playRecordRepo;
    private final ArticlePoolService articlePool;
    private final CachedArticleRepository cachedArticleRepo;
    private final CustomGenreRepository customGenreRepo;
    private final CommunityGenrePoolWarmer poolWarmer;
    private final DailyChallengeService dailyService;
    private final DailyChallengeRepository dailyRepo;
    private final PlayQuotaService quotaService;
    private final XpService xpService;
    private final ObjectMapper mapper = ArticleJson.MAPPER;
    private final Random random = new Random();

    public GameSessionService(
        GameSessionRepository sessionRepo,
        PlayRecordRepository playRecordRepo,
        ArticlePoolService articlePool,
        CachedArticleRepository cachedArticleRepo,
        CustomGenreRepository customGenreRepo,
        CommunityGenrePoolWarmer poolWarmer,
        DailyChallengeService dailyService,
        DailyChallengeRepository dailyRepo,
        PlayQuotaService quotaService,
        XpService xpService
    ) {
        this.sessionRepo = sessionRepo;
        this.playRecordRepo = playRecordRepo;
        this.articlePool = articlePool;
        this.cachedArticleRepo = cachedArticleRepo;
        this.customGenreRepo = customGenreRepo;
        this.poolWarmer = poolWarmer;
        this.dailyService = dailyService;
        this.dailyRepo = dailyRepo;
        this.quotaService = quotaService;
        this.xpService = xpService;
    }

    public record StartRequest(
        String mode,
        String genre,
        String scope,
        Long communityGenreId,
        Long dailyChallengeId,
        String difficulty,
        /** 記事の知名度 tier (1 = 常識レベル 〜 5 = 超マニアック)。null は指定なし。 */
        Integer fameTier,
        String playerId
    ) {}

    /** 呼び出し元 (ログインユーザー or 匿名 playerId)。 */
    public record Principal(User user, String playerId) {
        String playerKey() { return user != null ? "u" + user.getId() : playerId; }
    }

    // ------------------------------------------------------------------ start

    @Transactional
    public SessionView start(StartRequest req, Principal who) {
        String mode = req.mode() == null ? "a" : req.mode();
        if (!mode.equals("a") && !mode.equals("daily")) {
            throw new GameException(400, "対応していないモードです");
        }
        if (who.user() == null && (who.playerId() == null || who.playerId().isBlank())) {
            throw new GameException(400, "playerId が必要です");
        }

        GameSession s = new GameSession();
        s.setId(UUID.randomUUID().toString());
        s.setUserId(who.user() != null ? who.user().getId() : null);
        s.setPlayerId(who.user() == null ? who.playerId() : null);
        s.setMode(mode);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());

        List<ArticleData> articles;
        if (mode.equals("daily")) {
            if (who.user() == null) throw new GameException(401, "デイリーチャレンジはログインが必要です");
            DailyChallenge challenge = resolveDailyChallenge(req, who.user());
            if (dailyService.hasPlayed(challenge.getId(), who.playerKey())) {
                throw new GameException(409, "このチャレンジは既にプレイ済みです");
            }
            s.setDailyChallengeId(challenge.getId());
            s.setScope(challenge.getScopeKey().isEmpty() ? null : challenge.getScopeKey());
            s.setGenre(challenge.getGenreKey().isEmpty() ? null : challenge.getGenreKey());
            s.setDifficulty("normal");
            s.setRevealIntervalMs(10_000);
            articles = dailyService.articlesOf(challenge);
            if (articles.isEmpty()) throw new GameException(503, "今日の問題がまだ準備中です。少し待ってからもう一度試してください。");
        } else {
            if (!quotaService.canPlay(who.user(), who.playerId())) {
                throw new GameException(429, "今日のプレイ上限に達しました。プレミアムにアップグレードすると無制限に遊べます。");
            }
            s.setDifficulty(normalizeDifficulty(req.difficulty()));
            s.setRevealIntervalMs(intervalFor(s.getDifficulty()));
            if (req.communityGenreId() != null) {
                if (who.user() == null) throw new GameException(401, "コミュニティジャンルのプレイはログインが必要です");
                if (!quotaService.isPremium(who.user())) throw new GameException(402, "コミュニティジャンルのプレイはプレミアムプラン限定です");
                CustomGenre cg = customGenreRepo.findById(req.communityGenreId())
                    .orElseThrow(() -> new GameException(404, "コミュニティジャンルが見つかりません"));
                s.setCommunityGenreId(cg.getId());
                articles = pickCommunityArticles(cg);
                cg.setPlayCount(cg.getPlayCount() + 1);
            } else {
                String scope = blankToNull(req.scope());
                String genre = blankToNull(req.genre());
                s.setScope(genre == null ? null : scope);
                s.setGenre(genre);
                s.setFameTier(FameScorer.normalizeTier(req.fameTier()));
                articles = pickPoolArticles(s.getScope(), s.getGenre(), s.getFameTier());
            }
            if (articles.isEmpty()) throw new GameException(503, "適切な記事が見つかりませんでした。少し待ってからもう一度試してください。");
        }

        SessionState state = new SessionState();
        for (ArticleData a : articles) {
            SessionState.Question q = prepare(a, mode.equals("daily"));
            if (q != null) state.questions.add(q);
        }
        if (state.questions.isEmpty()) throw new GameException(503, "適切な記事が見つかりませんでした");
        state.questions.get(0).startedAtMs = System.currentTimeMillis();

        // play_record はセッション開始時に作り、終了時にスコアを書き戻す (途中放棄も 1 プレイに数える)
        PlayRecord r = new PlayRecord();
        r.setUserId(s.getUserId());
        r.setPlayerId(s.getPlayerId());
        r.setMode(mode);
        r.setGenre(s.getGenre());
        r.setScope(s.getScope());
        r.setCommunityGenreId(s.getCommunityGenreId());
        r.setScore(0);
        r.setMaxScore(MAX_SCORE_PER_QUESTION * state.questions.size());
        r.setDifficulty(s.getDifficulty());
        r.setPlayedAt(Instant.now());
        playRecordRepo.save(r);
        s.setPlayRecordId(r.getId());

        saveState(s, state);
        sessionRepo.save(s);
        return view(s, state);
    }

    private DailyChallenge resolveDailyChallenge(StartRequest req, User user) {
        if (req.dailyChallengeId() != null) {
            DailyChallenge c = dailyRepo.findById(req.dailyChallengeId())
                .orElseThrow(() -> new GameException(404, "チャレンジが見つかりません"));
            if (!c.getDate().equals(dailyService.today()) && !quotaService.isPremium(user)) {
                throw new GameException(402, "過去のデイリーチャレンジはプレミアム限定です");
            }
            return c;
        }
        return dailyService.getOrCreateToday(req.scope(), req.genre())
            .orElseThrow(() -> new GameException(503, "今日の問題がまだ準備中です。少し待ってからもう一度試してください。"));
    }

    /**
     * プールから 5 問分を選ぶ。
     * 知名度 tier 指定があればその tier (足りなければ近い tier) から優先して取り、
     * 全 tier 合わせても足りなければ tier を問わず補う
     * (プールが薄い間に 503 で遊べなくなるより、多少ずれた記事が混ざる方がまし)。
     */
    private List<ArticleData> pickPoolArticles(String scope, String genre, Integer fameTier) {
        List<ArticleData> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (fameTier != null) {
            for (ArticleData a : articlePool.pickByTier(scope, genre, fameTier, TOTAL_QUESTIONS * 2)) {
                if (out.size() >= TOTAL_QUESTIONS) break;
                if (seen.add(a.title()) && prepare(a, false) != null) out.add(a);
            }
            if (out.size() < TOTAL_QUESTIONS) {
                log.debug("fame tier {} pool thin for scope={} genre={} (got {}); topping up without tier",
                    fameTier, scope, genre, out.size());
            }
        }
        for (ArticleData a : articlePool.getRandomSample(scope, genre, TOTAL_QUESTIONS * 3)) {
            if (out.size() >= TOTAL_QUESTIONS) break;
            if (seen.add(a.title()) && prepare(a, false) != null) out.add(a);
        }
        // プールが薄いときは Wikipedia から直接補充
        for (int i = 0; i < 6 && out.size() < TOTAL_QUESTIONS; i++) {
            Optional<ArticleData> a = articlePool.getRandom(scope, genre);
            if (a.isPresent() && seen.add(a.get().title()) && prepare(a.get(), false) != null) out.add(a.get());
        }
        return out;
    }

    private List<ArticleData> pickCommunityArticles(CustomGenre cg) {
        List<ArticleData> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < TOTAL_QUESTIONS * 4 && out.size() < TOTAL_QUESTIONS; i++) {
            Optional<CachedArticle> c = seen.isEmpty()
                ? cachedArticleRepo.findRandomByCommunityGenreId(cg.getId())
                : cachedArticleRepo.findRandomByCommunityGenreIdExcluding(cg.getId(), seen);
            if (c.isEmpty()) break;
            seen.add(c.get().getTitle());
            try {
                ArticleData a = mapper.readValue(c.get().getDataJson(), ArticleData.class);
                if (prepare(a, false) != null) out.add(a);
            } catch (JsonProcessingException e) {
                log.warn("failed to deserialize cached article id={}", c.get().getId());
            }
        }
        if (out.size() < TOTAL_QUESTIONS) poolWarmer.warmGenreAsync(cg);
        return out;
    }

    /** 記事から問題を組み立てる。ゲームに向かない記事なら null。 */
    SessionState.Question prepare(ArticleData a, boolean lenient) {
        List<String> headings = a.sections() == null ? List.of() : a.sections().stream().map(sec -> sec.title()).toList();
        List<String> paragraphs = new ArrayList<>();
        for (String p : TextMasker.splitParagraphs(a.fullExtract(), headings)) {
            paragraphs.add(TextMasker.maskTitle(p, a.title(), a.aliases()));
        }
        if (paragraphs.isEmpty() && lenient && a.introExtract() != null && !a.introExtract().isBlank()) {
            paragraphs.add(TextMasker.maskTitle(a.introExtract(), a.title(), a.aliases()));
        }
        List<String> chars = CharInput.answerChars(a.title());
        if (paragraphs.size() < (lenient ? 1 : 2) || chars.isEmpty() || chars.size() > MAX_ANSWER_CHARS) return null;

        SessionState.Question q = new SessionState.Question();
        q.title = a.title();
        q.pageUrl = a.pageUrl();
        q.paragraphs = paragraphs;
        q.chars = chars;
        for (String c : chars) {
            q.options.add(CharInput.isSkip(c) ? List.of() : CharInput.options(c, a.fullExtract(), random));
        }
        q.pos = 0;
        advanceSkippable(q);
        return q;
    }

    // ------------------------------------------------------------------ actions

    @Transactional
    public SessionView get(String id, Principal who) {
        GameSession s = load(id, who);
        SessionState state = readState(s);
        return view(s, state);
    }

    @Transactional
    public SessionView reveal(String id, Principal who) {
        GameSession s = load(id, who);
        SessionState state = readState(s);
        SessionState.Question q = current(state);
        if (q.answered) throw new GameException(409, "この問題は回答済みです");
        if (q.lockedRevealed != null) throw new GameException(409, "入力開始後は段落を開示できません");
        int effective = effectiveRevealed(q, s.getRevealIntervalMs(), System.currentTimeMillis());
        q.revealed = Math.min(q.paragraphs.size(), effective + 1);
        saveState(s, state);
        return view(s, state);
    }

    @Transactional
    public SessionView answer(String id, String ch, Principal who) {
        GameSession s = load(id, who);
        SessionState state = readState(s);
        SessionState.Question q = current(state);
        if (q.answered) throw new GameException(409, "この問題は回答済みです");
        if (ch == null || ch.isEmpty()) throw new GameException(400, "文字を指定してください");
        long now = System.currentTimeMillis();
        if (q.lockedRevealed == null) {
            q.lockedRevealed = effectiveRevealed(q, s.getRevealIntervalMs(), now);
        }
        String expected = q.chars.get(q.pos);
        List<String> allowed = q.options.get(q.pos);
        if (!allowed.contains(ch)) throw new GameException(400, "選択肢にない文字です");
        if (ch.equals(expected)) {
            q.pos++;
            q.missedChar = null;
            advanceSkippable(q);
            if (q.pos >= q.chars.size()) finishQuestion(s, state, q, true, null);
        } else if (!q.lifeUsed) {
            q.lifeUsed = true;
            q.missedChar = ch;
        } else {
            finishQuestion(s, state, q, false, ch);
        }
        saveState(s, state);
        return view(s, state);
    }

    @Transactional
    public SessionView giveUp(String id, Principal who) {
        GameSession s = load(id, who);
        SessionState state = readState(s);
        SessionState.Question q = current(state);
        if (q.answered) throw new GameException(409, "この問題は回答済みです");
        if (q.lockedRevealed == null) {
            q.lockedRevealed = effectiveRevealed(q, s.getRevealIntervalMs(), System.currentTimeMillis());
        }
        finishQuestion(s, state, q, false, null);
        saveState(s, state);
        return view(s, state);
    }

    @Transactional
    public SessionView next(String id, Principal who) {
        GameSession s = load(id, who);
        SessionState state = readState(s);
        SessionState.Question q = current(state);
        if (!q.answered) throw new GameException(409, "まだ回答していません");
        if (state.current < state.questions.size() - 1) {
            state.current++;
            current(state).startedAtMs = System.currentTimeMillis();
        }
        saveState(s, state);
        return view(s, state);
    }

    // ------------------------------------------------------------------ core rules

    private static void advanceSkippable(SessionState.Question q) {
        while (q.pos < q.chars.size() && CharInput.isSkip(q.chars.get(q.pos))) q.pos++;
    }

    /**
     * 現時点で開示されているべき段落数。
     * 入力開始で固定済みならその値、そうでなければ「明示的な開示数」と「経過時間から求めた数」の大きい方。
     */
    static int effectiveRevealed(SessionState.Question q, int intervalMs, long nowMs) {
        int n = q.paragraphs.size();
        if (q.lockedRevealed != null) return Math.min(n, Math.max(1, q.lockedRevealed));
        long elapsed = Math.max(0, nowMs - q.startedAtMs - GRACE_MS);
        int byTime = 1 + (int) (elapsed / Math.max(1, intervalMs));
        return Math.min(n, Math.max(1, Math.max(q.revealed, byTime)));
    }

    /** 問題を終了して採点する。最終問題ならセッション全体も確定する。 */
    void finishQuestion(GameSession s, SessionState state, SessionState.Question q, boolean correct, String wrongChar) {
        int revealedCount = q.lockedRevealed != null
            ? q.lockedRevealed
            : effectiveRevealed(q, s.getRevealIntervalMs(), System.currentTimeMillis());
        q.revealedCount = revealedCount;
        q.answered = true;
        q.correct = correct;
        q.wrongChar = wrongChar;
        q.totalInputChars = (int) q.chars.stream().filter(c -> !CharInput.isSkip(c)).count();
        q.correctChars = (int) q.chars.subList(0, Math.min(q.pos, q.chars.size())).stream().filter(c -> !CharInput.isSkip(c)).count();
        q.score = scoreFor(correct, revealedCount, q.lifeUsed, q.correctChars, q.totalInputChars);

        if (state.current >= state.questions.size() - 1) {
            finishSession(s, state);
        }
    }

    static int scoreFor(boolean correct, int revealedCount, boolean lifeUsed, int correctChars, int totalInputChars) {
        int max = SCORES_BY_REVEAL[Math.min(Math.max(revealedCount, 1) - 1, SCORES_BY_REVEAL.length - 1)];
        if (correct) return lifeUsed ? max / 2 : max;
        if (totalInputChars <= 0) return 0;
        // 部分点は最大スコアの半分まで (完答とは大きな差をつける)
        return (int) Math.round(max * ((double) correctChars / totalInputChars) * 0.5);
    }

    private void finishSession(GameSession s, SessionState state) {
        int total = state.questions.stream().mapToInt(q -> q.score).sum();
        s.setTotalScore(total);
        s.setFinished(true);
        if (s.getPlayRecordId() != null) {
            playRecordRepo.findById(s.getPlayRecordId()).ifPresent(r -> {
                r.setScore(total);
                r.setPlayedAt(Instant.now());
                playRecordRepo.save(r);
            });
        }
        if (s.getUserId() != null) {
            User user = xpService.loadUser(s.getUserId());
            if (user != null) {
                XpService.AwardResult xp = xpService.award(user, total);
                state.xp = new SessionState.Xp();
                state.xp.gained = xp.gained();
                state.xp.capped = xp.capped();
                state.xp.leveledUp = xp.leveledUp();
                state.xp.xp = xp.info().xp();
                state.xp.level = xp.info().level();
                state.xp.xpIntoLevel = xp.info().xpIntoLevel();
                state.xp.xpForNextLevel = xp.info().xpForNextLevel();
                state.xp.dailyRemaining = xp.info().dailyRemaining();
                state.xp.streakDays = xp.info().streakDays();
                if (s.getDailyChallengeId() != null) {
                    state.dailyRecorded = dailyService.recordScore(
                        s.getDailyChallengeId(), "u" + user.getId(), user.getDisplayName(), total);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private GameSession load(String id, Principal who) {
        GameSession s = sessionRepo.findById(id).orElseThrow(() -> new GameException(404, "セッションが見つかりません"));
        boolean ok;
        if (s.getUserId() != null) {
            ok = who.user() != null && s.getUserId().equals(who.user().getId());
        } else {
            ok = who.playerId() != null && who.playerId().equals(s.getPlayerId());
        }
        if (!ok) throw new GameException(403, "このセッションにはアクセスできません");
        return s;
    }

    private static SessionState.Question current(SessionState state) {
        return state.questions.get(state.current);
    }

    private SessionState readState(GameSession s) {
        try {
            return mapper.readValue(s.getStateJson(), SessionState.class);
        } catch (JsonProcessingException e) {
            throw new GameException(500, "セッション状態の読み込みに失敗しました");
        }
    }

    private void saveState(GameSession s, SessionState state) {
        try {
            s.setStateJson(mapper.writeValueAsString(state));
            s.setUpdatedAt(Instant.now());
        } catch (JsonProcessingException e) {
            throw new GameException(500, "セッション状態の保存に失敗しました");
        }
    }

    SessionView view(GameSession s, SessionState state) {
        SessionState.Question q = current(state);
        long now = System.currentTimeMillis();
        int revealed = effectiveRevealed(q, s.getRevealIntervalMs(), now);
        int n = q.paragraphs.size();
        List<String> visible = q.paragraphs.subList(Math.max(0, n - revealed), n);

        List<SessionView.Slot> slots = new ArrayList<>();
        for (int i = 0; i < q.chars.size(); i++) {
            String c = q.chars.get(i);
            if (CharInput.isSkip(c)) {
                slots.add(new SessionView.Slot(c, null));
            } else if (i < q.pos) {
                // 入力済みの文字は開示してよい
                slots.add(new SessionView.Slot(null, List.of(c)));
            } else if (i == q.pos && !q.answered) {
                List<String> opts = new ArrayList<>(q.options.get(i));
                if (q.missedChar != null) opts.remove(q.missedChar);
                slots.add(new SessionView.Slot(null, opts));
            } else {
                slots.add(new SessionView.Slot(null, null));
            }
        }
        SessionView.QuestionResult result = q.answered ? toResult(q) : null;
        SessionView.QuestionView qv = new SessionView.QuestionView(
            n, revealed, visible, slots, q.chars.size(), q.pos, q.lifeUsed, q.missedChar,
            q.lockedRevealed != null, q.answered, result
        );

        SessionView.Summary summary = null;
        if (s.isFinished()) {
            List<SessionView.QuestionResult> results = state.questions.stream().map(GameSessionService::toResult).toList();
            SessionView.Xp xp = state.xp == null ? null : new SessionView.Xp(
                state.xp.gained, state.xp.capped, state.xp.leveledUp, state.xp.xp, state.xp.level,
                state.xp.xpIntoLevel, state.xp.xpForNextLevel, state.xp.dailyRemaining, state.xp.streakDays);
            summary = new SessionView.Summary(results, xp, state.dailyRecorded);
        }
        return new SessionView(
            s.getId(), s.getMode(), s.getGenre(), s.getScope(), s.getCommunityGenreId(), s.getDailyChallengeId(),
            s.getDifficulty(), s.getFameTier(), s.getRevealIntervalMs(), state.questions.size(), state.current,
            s.isFinished(), s.getTotalScore(), MAX_SCORE_PER_QUESTION * state.questions.size(), qv, summary
        );
    }

    private static SessionView.QuestionResult toResult(SessionState.Question q) {
        return new SessionView.QuestionResult(
            q.title, q.pageUrl, q.correct, q.score, q.revealedCount, q.correctChars, q.totalInputChars, q.wrongChar, q.lifeUsed
        );
    }

    static String normalizeDifficulty(String d) {
        if (d == null) return "normal";
        return switch (d) {
            case "relaxed", "speed" -> d;
            default -> "normal";
        };
    }

    static int intervalFor(String difficulty) {
        return switch (difficulty) {
            case "relaxed" -> 20_000;
            case "speed" -> 5_000;
            default -> 10_000;
        };
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank() || "random".equals(s)) return null;
        return s;
    }

    /** 2 日以上前のセッションを掃除する。 */
    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000L, initialDelay = 10 * 60 * 1000L)
    @Transactional
    public void cleanupOldSessions() {
        int n = sessionRepo.deleteOlderThan(Instant.now().minus(2, ChronoUnit.DAYS));
        if (n > 0) log.info("game sessions cleanup: deleted {}", n);
    }
}
