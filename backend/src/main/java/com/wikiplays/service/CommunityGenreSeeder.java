package com.wikiplays.service;

import com.wikiplays.entity.CustomGenre;
import com.wikiplays.repository.CustomGenreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * コミュニティジャンルテーブルが空のとき、Wikiplays 公式が用意した
 * シードジャンルを 20 件投入する。サービス開始直後でもユーザーが
 * 「眺める価値のあるリスト」を見られるようにする目的。
 *
 * Wikiplays 公式が作ったジャンルは creator_id="wikiplays-official" で
 * 識別し、誰からも削除できないようにする (Controller 側の削除 API でチェック)。
 */
@Component
public class CommunityGenreSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommunityGenreSeeder.class);

    public static final String OFFICIAL_CREATOR_ID = "wikiplays-official";
    private static final String OFFICIAL_CREATOR_NAME = "Wikiplays 公式";

    private final CustomGenreRepository repository;

    public CommunityGenreSeeder(CustomGenreRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 既存の公式ジャンル (wikiplays-official) を名前で索引
        java.util.Map<String, CustomGenre> existingByName = new java.util.HashMap<>();
        for (CustomGenre g : repository.findAll()) {
            if (OFFICIAL_CREATOR_ID.equals(g.getCreatorId())) {
                existingByName.put(g.getName(), g);
            }
        }

        int created = 0;
        int updated = 0;
        for (Seed s : SEEDS) {
            String desiredCategoriesCsv = String.join("\t", s.categories);
            CustomGenre entity = existingByName.get(s.name);
            if (entity == null) {
                // 新規作成
                entity = new CustomGenre();
                entity.setName(s.name);
                entity.setEmoji(s.emoji);
                entity.setCategoriesCsv(desiredCategoriesCsv);
                entity.setCreatorId(OFFICIAL_CREATOR_ID);
                entity.setCreatorName(OFFICIAL_CREATOR_NAME);
                entity.setCreatedAt(Instant.now());
                entity.setPlayCount(0);
                repository.save(entity);
                created++;
            } else {
                // 既存 → カテゴリ/絵文字 が変わっていれば更新 (playCount, createdAt は保持)
                boolean changed = false;
                if (!desiredCategoriesCsv.equals(entity.getCategoriesCsv())) {
                    entity.setCategoriesCsv(desiredCategoriesCsv);
                    changed = true;
                }
                if (!s.emoji.equals(entity.getEmoji())) {
                    entity.setEmoji(s.emoji);
                    changed = true;
                }
                if (changed) {
                    repository.save(entity);
                    updated++;
                }
            }
        }
        log.info("CommunityGenreSeeder: created={}, updated={}, total seeds={}",
            created, updated, SEEDS.size());
    }

    private record Seed(String emoji, String name, List<String> categories) {}

    // 各カテゴリは Wikipedia 日本語版に実在し、かつ実記事 (ns=0) を直接 or 1 段サブ
    // カテゴリ経由で含むものを選択している。
    private static final List<Seed> SEEDS = List.of(
        new Seed("♟️", "将棋棋士",       List.of("将棋棋士")),
        new Seed("🚄", "新幹線",         List.of("新幹線の車両", "日本の新幹線")),
        new Seed("🦖", "恐竜",           List.of("竜盤類", "鳥盤類", "獣脚類")),
        new Seed("🐉", "ポケモン",       List.of("ポケットモンスター (架空の生物)")),
        new Seed("🌋", "火山",           List.of("日本の火山", "世界の火山")),
        new Seed("🎬", "スタジオジブリ", List.of("スタジオジブリのアニメ映画")),
        new Seed("🍜", "麺料理",         List.of("ラーメン", "うどん", "そば")),
        new Seed("🏰", "戦国武将",       List.of("戦国時代の人物 (日本)", "戦国大名")),
        new Seed("⚾", "野球選手",       List.of("日本の野球選手", "メジャーリーグベースボールの選手")),
        new Seed("🎮", "ファミコンソフト", List.of("ファミリーコンピュータ用ソフト")),
        new Seed("🏯", "日本の城",       List.of("各都道府県の城")),
        new Seed("🎌", "都道府県",       List.of("日本の都道府県")),
        new Seed("🏛️", "世界遺産",       List.of("国別の世界遺産", "日本の世界遺産")),
        new Seed("🔬", "化学元素",       List.of("元素")),
        new Seed("🐱", "哺乳類",         List.of("食肉目", "霊長目", "齧歯目")),
        new Seed("✈️", "航空機",         List.of("旅客機", "ボーイングの航空機")),
        new Seed("🎲", "ボードゲーム",   List.of("ボードゲーム")),
        new Seed("🦅", "鳥類",           List.of("スズメ目", "タカ目", "ハト目")),
        new Seed("🎭", "落語",           List.of("落語家")),
        new Seed("🚙", "日本車",         List.of("トヨタ自動車の車種", "日産自動車の車種", "本田技研工業の車種"))
    );
}
