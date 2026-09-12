package com.wikiplays.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    /**
     * Wikipedia API 用 WebClient。
     * 全呼び出しが .block() で待つため、タイムアウト無しだと Wikipedia が遅いときに
     * リクエストスレッドを永久に占有してしまう。接続 5 秒 / 応答 15 秒で必ず諦める。
     */
    @Bean
    public WebClient wikipediaWebClient(
        @Value("${wikiplays.wikipedia.connect-timeout-ms:5000}") int connectTimeoutMs,
        @Value("${wikiplays.wikipedia.response-timeout-ms:15000}") int responseTimeoutMs
    ) {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(responseTimeoutMs))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(responseTimeoutMs, TimeUnit.MILLISECONDS))
                .addHandlerLast(new WriteTimeoutHandler(responseTimeoutMs, TimeUnit.MILLISECONDS)));
        return WebClient.builder()
            .baseUrl("https://ja.wikipedia.org")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("User-Agent", "Wikiplays/0.2 (https://wikiplays.me; quiz game)")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
            .build();
    }
}
