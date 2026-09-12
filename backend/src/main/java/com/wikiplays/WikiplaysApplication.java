package com.wikiplays;

import com.wikiplays.service.ArticlePoolService;
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

    @Autowired
    private ArticlePoolService articlePoolService;

    @org.springframework.beans.factory.annotation.Value("${wikiplays.pool.initial-populate:true}")
    private boolean initialPopulate;

    public static void main(String[] args) {
        SpringApplication.run(WikiplaysApplication.class, args);
    }

    /** アプリ起動完了後、非同期で記事プールを初期補充する (起動を遅らせないため Async)。 */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmUpPool() {
        if (initialPopulate) articlePoolService.initialPopulate();
    }
}
