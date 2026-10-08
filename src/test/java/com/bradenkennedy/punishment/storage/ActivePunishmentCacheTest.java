package com.bradenkennedy.punishment.storage;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivePunishmentCacheTest {

    private final ActivePunishmentCache cache = new ActivePunishmentCache();
    private final UUID player = UUID.randomUUID();

    @Test
    void findsPutPunishment() {
        var mute = mute(player, null);
        cache.put(mute);
        assertEquals(mute, cache.find(player, PunishmentType.MUTE).orElseThrow());
    }

    @Test
    void treatsExpiredPunishmentAsAbsentAndRemovesIt() {
        cache.put(mute(player, Instant.now().minus(Duration.ofSeconds(1))));
        assertTrue(cache.find(player, PunishmentType.MUTE).isEmpty());
        assertTrue(cache.find(player, PunishmentType.MUTE).isEmpty());
    }

    @Test
    void keepsUnexpiredTempPunishment() {
        cache.put(mute(player, Instant.now().plus(Duration.ofMinutes(5))));
        assertTrue(cache.find(player, PunishmentType.MUTE).isPresent());
    }

    @Test
    void removeClearsOnlyThatType() {
        cache.put(mute(player, null));
        cache.remove(player, PunishmentType.MUTE);
        assertTrue(cache.find(player, PunishmentType.MUTE).isEmpty());
    }

    @Test
    void evictClearsPlayerButNotOthers() {
        var other = UUID.randomUUID();
        cache.put(mute(player, null));
        cache.put(mute(other, null));
        cache.evict(player);
        assertTrue(cache.find(player, PunishmentType.MUTE).isEmpty());
        assertTrue(cache.find(other, PunishmentType.MUTE).isPresent());
    }

    private static Punishment mute(UUID target, Instant expiry) {
        return new Punishment(UUID.randomUUID(), target, PunishmentType.MUTE,
                new PunishmentIssuer(UUID.randomUUID(), Instant.now()), null, expiry, false);
    }
}
