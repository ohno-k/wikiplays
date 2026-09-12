package com.wikiplays.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DummyUserSeederTest {

    @Test
    void provisionalNamesAreDetected() {
        assertTrue(DummyUserSeeder.isProvisionalName("ねむいパンダ"));
        assertTrue(DummyUserSeeder.isProvisionalName("でんせつの学芸員"));
        assertTrue(DummyUserSeeder.isProvisionalName("yuki_12"));
        assertTrue(DummyUserSeeder.isProvisionalName("daichi7"));
        assertTrue(DummyUserSeeder.isProvisionalName("カピバラ123"));
        assertTrue(DummyUserSeeder.isProvisionalName("player42"));
    }

    @Test
    void wikipediaStyleNamesAreNotProvisional() {
        assertFalse(DummyUserSeeder.isProvisionalName(null));
        assertFalse(DummyUserSeeder.isProvisionalName("Sakura1985"));
        assertFalse(DummyUserSeeder.isProvisionalName("たけし"));
        assertFalse(DummyUserSeeder.isProvisionalName("山田花子"));
        assertFalse(DummyUserSeeder.isProvisionalName("yuki_123"));   // 3 桁は生成しない
        assertFalse(DummyUserSeeder.isProvisionalName("ねむいひと"));
        assertFalse(DummyUserSeeder.isProvisionalName("パンダ12"));
    }
}
