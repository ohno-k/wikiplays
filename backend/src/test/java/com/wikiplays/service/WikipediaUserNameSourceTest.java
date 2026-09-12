package com.wikiplays.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WikipediaUserNameSourceTest {

    private static final String SAMPLE = """
        {"batchcomplete":"","query":{"allusers":[
          {"userid":1,"name":"Sakura1985"},
          {"userid":2,"name":"たけし"},
          {"userid":3,"name":"SomeBot"},
          {"userid":4,"name":"192.168.0.1"},
          {"userid":5,"name":"Taro (WMF)"},
          {"userid":6,"name":"Renamed user 12345"},
          {"userid":7,"name":"ウィキ太郎"},
          {"userid":8,"name":"Kazu_M"},
          {"userid":9,"name":"12345678"},
          {"userid":10,"name":"x"},
          {"userid":11,"name":"aaaaaaa"},
          {"userid":12,"name":"山田花子"}
        ]}}
        """;

    @Test
    void usableNamesLookLikeOrdinaryHandles() {
        assertTrue(WikipediaUserNameSource.isUsable("Sakura1985"));
        assertTrue(WikipediaUserNameSource.isUsable("たけし"));
        assertTrue(WikipediaUserNameSource.isUsable("Kazu_M"));
        assertTrue(WikipediaUserNameSource.isUsable("山田花子"));
        assertTrue(WikipediaUserNameSource.isUsable("Yuki Tanaka"));
    }

    @Test
    void obviouslyNonHumanNamesAreRejected() {
        assertFalse(WikipediaUserNameSource.isUsable(null));
        assertFalse(WikipediaUserNameSource.isUsable(""));
        assertFalse(WikipediaUserNameSource.isUsable("x"));
        assertFalse(WikipediaUserNameSource.isUsable("SomeBot"));
        assertFalse(WikipediaUserNameSource.isUsable("ボット太郎"));
        assertFalse(WikipediaUserNameSource.isUsable("192.168.0.1"));
        assertFalse(WikipediaUserNameSource.isUsable("Taro (WMF)"));
        assertFalse(WikipediaUserNameSource.isUsable("Renamed user 12345"));
        assertFalse(WikipediaUserNameSource.isUsable("Vanished user abc"));
        assertFalse(WikipediaUserNameSource.isUsable("ウィキ太郎"));
        assertFalse(WikipediaUserNameSource.isUsable("12345678"));
        assertFalse(WikipediaUserNameSource.isUsable("aaaaaaa"));
        assertFalse(WikipediaUserNameSource.isUsable("Test account"));
        assertFalse(WikipediaUserNameSource.isUsable("a/b"));
        assertFalse(WikipediaUserNameSource.isUsable("this name is far too long to be natural"));
    }

    @Test
    void parseKeepsOnlyUsableNames() throws Exception {
        List<String> names = WikipediaUserNameSource.parse(new ObjectMapper().readTree(SAMPLE));
        assertEquals(List.of("Sakura1985", "たけし", "Kazu_M", "山田花子"), names);
    }

    @Test
    void fetchReturnsShuffledUniqueNamesUpToWanted() {
        AtomicInteger calls = new AtomicInteger();
        WebClient client = WebClient.builder()
            .exchangeFunction(req -> {
                calls.incrementAndGet();
                assertTrue(req.url().toString().contains("list=allusers"));
                assertTrue(req.url().toString().contains("aufrom="));
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(SAMPLE).build());
            })
            .build();
        WikipediaUserNameSource source = new WikipediaUserNameSource(client);

        List<String> names = source.fetch(3);
        assertEquals(3, names.size());
        assertEquals(3, names.stream().distinct().count());
        assertTrue(List.of("Sakura1985", "たけし", "Kazu_M", "山田花子").containsAll(names));
        assertTrue(calls.get() >= 1);
    }

    @Test
    void fetchReturnsEmptyWhenWikipediaIsUnreachable() {
        WebClient client = WebClient.builder()
            .exchangeFunction(req -> Mono.error(new IllegalStateException("connection refused")))
            .build();
        WikipediaUserNameSource source = new WikipediaUserNameSource(client);
        assertTrue(source.fetch(50).isEmpty());
        assertTrue(source.fetch(0).isEmpty());
    }

    @Test
    void startKeysVaryAndAreShort() {
        WikipediaUserNameSource source = new WikipediaUserNameSource(WebClient.create());
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            String k = source.randomStartKey();
            assertTrue(k.length() >= 1 && k.length() <= 2, k);
            keys.add(k);
        }
        assertTrue(keys.size() > 20);
    }
}
