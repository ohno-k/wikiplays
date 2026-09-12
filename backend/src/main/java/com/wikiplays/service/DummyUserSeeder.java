package com.wikiplays.service;

import com.wikiplays.entity.DailyChallenge;
import com.wikiplays.entity.DailyScore;
import com.wikiplays.entity.PlayRecord;
import com.wikiplays.entity.User;
import com.wikiplays.repository.DailyScoreRepository;
import com.wikiplays.repository.PlayRecordRepository;
import com.wikiplays.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 運営が投入する架空ユーザー (ダミー)。
 *
 * サービス初期にランキングやデイリーが空だと寂しいので、約 100 人分のユーザーと
 * 過去 60 日分のプレイ履歴を生成し、以後も 1 日数回「今日のプレイ」を追加して動いているように見せる。
 *
 * 表示名は日本語版 Wikipedia の利用者一覧から拾った実在のハンドル ({@link WikipediaUserNameSource}) を使う。
 * 単語合成のダミー名は並ぶとそれと分かるため、Wikipedia に到達できない時だけの暫定名とし、
 * 次回起動時に Wikipedia 由来の名前へ付け替える。
 *
 * 全員 app_user.is_dummy = true で識別でき、ログインはできない。停止・削除は README 参照。
 * 設定: wikiplays.seed.dummy-users.enabled / count
 */
@Component
public class DummyUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(DummyUserSeeder.class);
    private static final ZoneId TZ = ZoneId.of("Asia/Tokyo");
    public static final String DUMMY_EMAIL_DOMAIN = "dummy.wikiplays.invalid";

    private static final String[] GENRES = {
        "rail", "history", "geography", "science", "biology", "plant", "paleontology", "astronomy",
        "art", "literature", "music", "movie", "anime", "sports", "mythology", "architecture",
        "vehicle", "language", "computer", "food"
    };
    private static final String[] DIFFICULTIES = {"relaxed", "normal", "normal", "normal", "speed"};

    // ---- 暫定名 (Wikipedia に到達できない時のフォールバック)。このパターンに一致する名前は
    //      isProvisionalName() で検出でき、次回起動時に Wikipedia 由来の名前へ付け替える。
    private static final String[] NAME_PREFIX = {
        "ねむい", "はらぺこ", "よふかし", "まったり", "はやおき", "さすらいの", "しずかな", "こだわり",
        "つよがり", "のんびり", "うっかり", "きまぐれ", "ひかえめ", "がんばる", "なぞの", "ほんきの",
        "やさしい", "ふらっと", "でんせつの", "みならい", "ゆるふわ", "こんじょう", "ひみつの", "おちゃめな"
    };
    private static final String[] NAME_SUFFIX = {
        "パンダ", "たぬき", "ペンギン", "カピバラ", "シロクマ", "ハリネズミ", "うさぎ", "フクロウ",
        "カワウソ", "きつね", "ラッコ", "アルパカ", "コアラ", "ねこ", "いぬ", "くじら", "かめ", "りす",
        "司書", "駅員", "教授", "研究員", "旅人", "探偵", "編集者", "学芸員"
    };
    private static final String[] LATIN_NAMES = {
        "yuki", "tomo", "kenta", "mio", "haru", "sora", "rin", "kai", "nao", "ryo", "aki", "hina",
        "taku", "saki", "shun", "emi", "ren", "mei", "kazu", "yui", "daichi", "mika", "sho", "nana"
    };

    private final UserRepository userRepository;
    private final PlayRecordRepository playRecordRepository;
    private final DailyChallengeService dailyService;
    private final DailyScoreRepository dailyScoreRepository;
    private final WikipediaUserNameSource nameSource;
    private final boolean enabled;
    private final int targetCount;
    private final Random random = new Random();

    public DummyUserSeeder(
        UserRepository userRepository,
        PlayRecordRepository playRecordRepository,
        DailyChallengeService dailyService,
        DailyScoreRepository dailyScoreRepository,
        WikipediaUserNameSource nameSource,
        @Value("${wikiplays.seed.dummy-users.enabled:false}") boolean enabled,
        @Value("${wikiplays.seed.dummy-users.count:100}") int targetCount
    ) {
        this.userRepository = userRepository;
        this.playRecordRepository = playRecordRepository;
        this.dailyService = dailyService;
        this.dailyScoreRepository = dailyScoreRepository;
        this.nameSource = nameSource;
        this.enabled = enabled;
        this.targetCount = targetCount;
    }

    /** 起動後に、暫定名のままのユーザーを付け替え、不足分を投入する (既に十分いれば何もしない)。 */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void seedOnStartup() {
        if (!enabled) return;
        try {
            int renamed = renameProvisional();
            if (renamed > 0) log.info("DummyUserSeeder: renamed {} provisional dummy users", renamed);
        } catch (Exception e) {
            log.warn("DummyUserSeeder rename failed: {}", e.getMessage());
        }
        try {
            int created = seedMissing();
            if (created > 0) log.info("DummyUserSeeder: created {} dummy users", created);
        } catch (Exception e) {
            log.warn("DummyUserSeeder failed: {}", e.getMessage());
        }
    }

    /**
     * 暫定名 (単語合成のフォールバック名) のダミーユーザーを、Wikipedia 由来の名前に付け替える。
     * デイリーランキングは daily_score に表示名を持つので、そちらも揃えて書き換える。
     * Wikipedia から名前が取れなければ何もしない (次回起動時に再試行)。
     */
    @Transactional
    public int renameProvisional() {
        List<User> dummies = userRepository.findByDummyTrue();
        List<User> provisional = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (User u : dummies) {
            usedNames.add(u.getDisplayName());
            if (isProvisionalName(u.getDisplayName())) provisional.add(u);
        }
        if (provisional.isEmpty()) return 0;

        Deque<String> pool = new ArrayDeque<>(nameSource.fetch(provisional.size() + 10));
        int renamed = 0;
        for (User u : provisional) {
            String name = pollUnused(pool, usedNames);
            if (name == null) break;
            String playerId = "u" + u.getId();
            for (DailyScore ds : dailyScoreRepository.findByPlayerId(playerId)) {
                ds.setDisplayName(name);
                dailyScoreRepository.save(ds);
            }
            u.setDisplayName(name);
            userRepository.save(u);
            renamed++;
        }
        return renamed;
    }

    @Transactional
    public int seedMissing() {
        long existing = userRepository.countByDummyTrue();
        int missing = (int) Math.max(0, targetCount - existing);
        if (missing == 0) return 0;

        Set<String> usedNames = new HashSet<>();
        for (User u : userRepository.findByDummyTrue()) usedNames.add(u.getDisplayName());
        Deque<String> pool = new ArrayDeque<>(nameSource.fetch(missing + 10));
        LocalDate today = LocalDate.now(TZ);
        int created = 0;
        for (int i = 0; i < missing; i++) {
            User u = new User();
            u.setEmail("dummy-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@" + DUMMY_EMAIL_DOMAIN);
            // BCrypt 形式ではない値なので、どのパスワードでも一致しない (加えて AuthService でも拒否)
            u.setPasswordHash("!dummy-" + new java.math.BigInteger(80, new SecureRandom()).toString(36));
            u.setDisplayName(uniqueName(pool, usedNames));
            u.setRole("USER");
            u.setEmailVerified(true);
            u.setDummy(true);
            // 登録日は 10〜180 日前
            Instant createdAt = Instant.now().minus(10 + random.nextInt(170), ChronoUnit.DAYS);
            u.setCreatedAt(createdAt);
            userRepository.save(u);

            // プレイ履歴を生成し、XP はその履歴から導出する (レベルと履歴が矛盾しないように)
            Profile p = randomProfile();
            long xp = generateHistory(u, p, today);
            u.setXp(xp);
            u.setXpDay(today);
            u.setXpEarnedToday(0);
            if (p.streak > 0) {
                u.setStreakDays(p.streak);
                u.setLastPlayDate(random.nextInt(3) == 0 ? today.minusDays(1) : today);
            } else {
                u.setStreakDays(0);
                u.setLastPlayDate(today.minusDays(2 + random.nextInt(20)));
            }
            u.setLastLoginAt(u.getLastPlayDate().atStartOfDay(TZ).toInstant().plus(9 + random.nextInt(12), ChronoUnit.HOURS));
            userRepository.save(u);
            created++;
        }
        return created;
    }

    /** ユーザーごとの傾向: 実力・熱心さ・連続日数。 */
    private record Profile(double skill, int plays, int streak) {}

    private Profile randomProfile() {
        // 実力: 平均 0.55、0.2〜0.95 に収める
        double skill = clamp(0.55 + random.nextGaussian() * 0.17, 0.2, 0.95);
        // プレイ数: 少数の熱心なユーザーがいる分布 (3〜150)
        double r = random.nextDouble();
        int plays = r < 0.5 ? 3 + random.nextInt(15)
            : r < 0.85 ? 18 + random.nextInt(40)
            : 60 + random.nextInt(90);
        int streak = random.nextDouble() < 0.45 ? 0 : 1 + random.nextInt(random.nextDouble() < 0.8 ? 7 : 30);
        return new Profile(skill, plays, streak);
    }

    /** 過去 60 日にプレイ履歴を散らばせ、獲得 XP の合計を返す。 */
    private long generateHistory(User u, Profile p, LocalDate today) {
        long xp = 0;
        java.util.Map<LocalDate, Integer> earnedByDay = new java.util.HashMap<>();
        for (int i = 0; i < p.plays; i++) {
            // 直近ほど多め (二乗分布)
            int daysAgo = (int) (Math.pow(random.nextDouble(), 2) * 60);
            LocalDate day = today.minusDays(daysAgo);
            Instant at = day.atStartOfDay(TZ).toInstant()
                .plus(7 + random.nextInt(16), ChronoUnit.HOURS)
                .plus(random.nextInt(60), ChronoUnit.MINUTES);
            PlayRecord r = randomRecord(u, p, at);
            playRecordRepository.save(r);

            int gained = XpService.xpForPlay(r.getScore());
            int already = earnedByDay.getOrDefault(day, 0);
            int capped = Math.min(gained, Math.max(0, XpService.DAILY_CAP - already));
            earnedByDay.put(day, already + capped);
            xp += capped;
        }
        return xp;
    }

    private PlayRecord randomRecord(User u, Profile p, Instant at) {
        PlayRecord r = new PlayRecord();
        r.setUserId(u.getId());
        double m = random.nextDouble();
        String mode = m < 0.5 ? "a" : m < 0.68 ? "daily" : m < 0.78 ? "b" : m < 0.86 ? "c" : m < 0.94 ? "d" : "e";
        r.setMode(mode);
        if (!mode.equals("daily") && random.nextDouble() < 0.7) {
            r.setGenre(GENRES[random.nextInt(GENRES.length)]);
            r.setScope(random.nextDouble() < 0.65 ? "jp" : "world");
        }
        if (mode.equals("a")) r.setDifficulty(DIFFICULTIES[random.nextInt(DIFFICULTIES.length)]);
        r.setMaxScore(5000);
        r.setScore(randomScore(p.skill));
        r.setPlayedAt(at);
        return r;
    }

    /** 実力に応じたスコア (0〜5000)。5 問それぞれの出来を模して合成する。 */
    int randomScore(double skill) {
        int total = 0;
        for (int q = 0; q < 5; q++) {
            double roll = random.nextDouble();
            if (roll > skill) {
                // 不正解: 部分点 0〜300
                total += random.nextInt(4) == 0 ? 0 : random.nextInt(300);
            } else {
                // 正解: 開示段落数で 100〜1000
                int[] byReveal = {1000, 800, 600, 400, 200, 100};
                int idx = (int) clamp(Math.floor((1 - skill) * 6 + random.nextGaussian() * 1.2), 0, 5);
                total += byReveal[idx];
                if (random.nextDouble() < 0.15) total -= byReveal[idx] / 2; // ライフ使用
            }
        }
        return Math.max(0, Math.min(5000, total));
    }

    /** Wikipedia 由来の名前を優先し、尽きたら暫定名 (単語合成) にフォールバックする。 */
    private String uniqueName(Deque<String> pool, Set<String> used) {
        String fromPool = pollUnused(pool, used);
        if (fromPool != null) return fromPool;
        return provisionalName(used);
    }

    /** プールから未使用の名前を 1 つ取り出す。無ければ null。 */
    private static String pollUnused(Deque<String> pool, Set<String> used) {
        while (!pool.isEmpty()) {
            String name = pool.poll();
            if (name != null && !name.isBlank() && used.add(name)) return name;
        }
        return null;
    }

    private String provisionalName(Set<String> used) {
        for (int attempt = 0; attempt < 200; attempt++) {
            String name;
            double r = random.nextDouble();
            if (r < 0.6) {
                name = NAME_PREFIX[random.nextInt(NAME_PREFIX.length)] + NAME_SUFFIX[random.nextInt(NAME_SUFFIX.length)];
            } else if (r < 0.85) {
                name = LATIN_NAMES[random.nextInt(LATIN_NAMES.length)]
                    + (random.nextBoolean() ? "_" : "") + (random.nextInt(98) + 1);
            } else {
                name = NAME_SUFFIX[random.nextInt(NAME_SUFFIX.length)] + (random.nextInt(900) + 100);
            }
            if (used.add(name)) return name;
        }
        return "player" + random.nextInt(100_000);
    }

    private static final Pattern PROVISIONAL_LATIN = Pattern.compile(
        "^(" + String.join("|", LATIN_NAMES) + ")_?\\d{1,2}$");
    private static final Pattern PROVISIONAL_SUFFIX_NUMBER = Pattern.compile(
        "^(" + String.join("|", NAME_SUFFIX) + ")\\d{3}$");
    private static final Pattern PROVISIONAL_PLAYER = Pattern.compile("^player\\d{1,5}$");

    /** provisionalName() が生成しうる名前か (旧バージョンで投入された名前も含む)。 */
    static boolean isProvisionalName(String name) {
        if (name == null) return false;
        if (PROVISIONAL_LATIN.matcher(name).matches()) return true;
        if (PROVISIONAL_SUFFIX_NUMBER.matcher(name).matches()) return true;
        if (PROVISIONAL_PLAYER.matcher(name).matches()) return true;
        for (String prefix : NAME_PREFIX) {
            if (!name.startsWith(prefix)) continue;
            String rest = name.substring(prefix.length());
            for (String suffix : NAME_SUFFIX) {
                if (rest.equals(suffix)) return true;
            }
        }
        return false;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ 日次活動

    /**
     * 2 時間ごとに、ダミーユーザーの一部が「今日プレイした」ことにする。
     * - 通常モードのプレイ履歴を数件追加 (今日/今週ランキング用)
     * - 今日の総合デイリーチャレンジに未参加のユーザーが少しずつ参加する
     */
    @Scheduled(cron = "0 7 6-23/2 * * *", zone = "Asia/Tokyo")
    @Transactional
    public void dailyActivity() {
        if (!enabled) return;
        List<User> dummies = userRepository.findByDummyTrue();
        if (dummies.isEmpty()) return;
        LocalDate today = LocalDate.now(TZ);
        Optional<DailyChallenge> todays = dailyService.getOrCreateToday(null, null);

        int added = 0;
        for (User u : dummies) {
            Profile p = new Profile(skillOf(u), 0, 0);
            // 熱心さ (XP が高いほど活動的): 1 回の実行で活動する確率 4〜20%
            double activity = 0.04 + Math.min(0.16, u.getXp() / 30_000.0);
            if (random.nextDouble() >= activity) continue;

            Instant now = Instant.now().minus(random.nextInt(90), ChronoUnit.MINUTES);
            boolean doDaily = todays.isPresent() && random.nextDouble() < 0.45
                && !dailyService.hasPlayed(todays.get().getId(), "u" + u.getId());
            PlayRecord r = randomRecord(u, p, now);
            if (doDaily) {
                r.setMode("daily");
                r.setGenre(null);
                r.setScope(null);
                r.setDifficulty(null);
                dailyService.recordScore(todays.get().getId(), "u" + u.getId(), u.getDisplayName(), r.getScore());
            } else if (r.getMode().equals("daily")) {
                r.setMode("a");
                r.setDifficulty("normal");
            }
            playRecordRepository.save(r);

            // XP / ストリーク更新 (実ユーザーと同じルール)
            if (!today.equals(u.getXpDay())) { u.setXpDay(today); u.setXpEarnedToday(0); }
            int gained = Math.min(XpService.xpForPlay(r.getScore()), Math.max(0, XpService.DAILY_CAP - u.getXpEarnedToday()));
            u.setXp(u.getXp() + gained);
            u.setXpEarnedToday(u.getXpEarnedToday() + gained);
            if (!today.equals(u.getLastPlayDate())) {
                u.setStreakDays(today.minusDays(1).equals(u.getLastPlayDate()) ? u.getStreakDays() + 1 : 1);
                u.setLastPlayDate(today);
            }
            u.setLastLoginAt(now);
            userRepository.save(u);
            added++;
        }
        if (added > 0) log.info("DummyUserSeeder: daily activity added {} plays", added);
    }

    /** XP から実力を推定 (レベルの高いユーザーほど高スコア傾向)。 */
    private double skillOf(User u) {
        int level = XpService.levelFromXp(u.getXp());
        return clamp(0.4 + level * 0.02 + random.nextGaussian() * 0.08, 0.2, 0.95);
    }
}
