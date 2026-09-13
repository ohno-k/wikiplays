package com.wikiplays;

import com.wikiplays.service.ArticlePoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class WikiplaysApplication {

    private static final Logger log = LoggerFactory.getLogger(WikiplaysApplication.class);

    @Autowired
    private ArticlePoolService articlePoolService;

    @org.springframework.beans.factory.annotation.Value("${wikiplays.pool.initial-populate:true}")
    private boolean initialPopulate;

    public static void main(String[] args) {
        SpringApplication.run(WikiplaysApplication.class, args);
    }

    /**
     * アプリ起動完了後、非同期で記事プールを整える (起動を遅らせないため Async)。
     * 1. 知名度スコア未計算の旧キャッシュを埋める (fame_score 列追加前 / 単位変更前の記事)
     * 2. 各バケットを少量だけ即補充 (常識レベルの記事を含む)
     * 3. 閲覧数未取得の旧キャッシュに Wikipedia の閲覧数を書き込み、スコアを実測値に置き換える
     *    (時間がかかるので出題に必要な補充の後に回す)
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmUpPool() {
        try {
            articlePoolService.backfillFameScores();
        } catch (Exception e) {
            log.warn("fame score backfill failed: {}", e.getMessage());
        }
        if (initialPopulate) articlePoolService.initialPopulate();
        try {
            articlePoolService.backfillPageViews();
        } catch (Exception e) {
            log.warn("page view backfill failed: {}", e.getMessage());
        }
    }
}
