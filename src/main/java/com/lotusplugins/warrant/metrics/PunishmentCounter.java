package com.bradenkennedy.punishment.metrics;

import java.util.concurrent.atomic.AtomicInteger;

public final class PunishmentCounter {

    private final AtomicInteger issued = new AtomicInteger();

    public void increment() {
        issued.incrementAndGet();
    }

    public int drain() {
        return issued.getAndSet(0);
    }
}
