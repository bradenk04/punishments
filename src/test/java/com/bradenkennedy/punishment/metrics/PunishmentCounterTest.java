package com.bradenkennedy.punishment.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PunishmentCounterTest {

    private final PunishmentCounter counter = new PunishmentCounter();

    @Test
    void startsAtZero() {
        assertEquals(0, counter.drain());
    }

    @Test
    void drainReturnsCountAndResets() {
        counter.increment();
        counter.increment();

        assertEquals(2, counter.drain());
        assertEquals(0, counter.drain());
    }
}
