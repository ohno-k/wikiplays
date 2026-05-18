package com.wikiplays.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient wikipediaWebClient() {
        return WebClient.builder()
            .baseUrl("https://ja.wikipedia.org")
            .defaultHeader("User-Agent", "Wikiplays/0.1 (https://github.com/; quiz game)")
            .build();
    }
}
