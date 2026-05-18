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
        if (repository.count() > 0) {
            log.info("CommunityGenreSeeder: skip (already has {} entries)", repository.count());
            return;
        }
        log.info("CommunityGenreSeeder: seeding 20 official community genres");
        for (Seed s : SEEDS) {
            CustomGenre entity = new CustomGenre();
            entity.setName(s.name);
            entity.setEmoji(s.emoji);
            entity.setCategoriesCsv(String.join("\t", s.categories));
            entity.setCreatorId(OFFICIAL_CREATOR_ID);
            entity.setCreatorName(OFFICIAL_CREATOR_NAME);
            entity.setCreatedAt(Instant.now());
            entity.setPlayCount(0);
            repository.save(entity);
        }
    }

    private record Seed(String emoji, String name, List<String> categories) {}

    private static final List<Seed> SEEDS = List.of(
        new Seed("♟️", "将棋棋士",       List.of("日本の将棋棋士")),
        new Seed("🚄", "新幹線",         List.of("新幹線")),
        new Seed("🦖", "恐竜",           List.of("恐竜")),
        new Seed("🐉", "ポケモン",       List.of("ポケモン")),
        new Seed("🌋", "火山",           List.of("火山", "日本の火山")),
        new Seed("🎬", "スタジオジブリ", List.of("スタジオジブリの作品")),
        new Seed("🍜", "麺料理",         List.of("麺料理", "ラーメン", "うどん")),
        new Seed("🏰", "戦国武将",       List.of("戦国武将")),
        new Seed("⚾", "プロ野球選手",   List.of("日本のプロ野球選手")),
        new Seed("🎮", "ファミコンソフト", List.of("ファミリーコンピュータ用ソフト")),
        new Seed("🏯", "日本の城",       List.of("日本の城")),
        new Seed("🎌", "都道府県",       List.of("日本の都道府県")),
        new Seed("🏛️", "世界遺産",       List.of("世界遺産")),
        new Seed("🔬", "化学元素",       List.of("化学元素")),
        new Seed("🐱", "哺乳類",         List.of("哺乳類")),
        new Seed("✈️", "航空機",         List.of("航空機", "旅客機")),
        new Seed("🎲", "ボードゲーム",   List.of("ボードゲーム")),
        new Seed("🦅", "鳥類",           List.of("鳥類")),
        new Seed("🎭", "落語",           List.of("日本の落語家", "落語")),
        new Seed("🚙", "日本車",         List.of("日本車", "トヨタ自動車の車種"))
    );
}
