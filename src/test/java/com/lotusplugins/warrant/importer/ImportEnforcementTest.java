package com.lotusplugins.warrant.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lotusplugins.warrant.ServerTestBase;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentIssuer;
import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.api.model.Revocation;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportEnforcementTest extends ServerTestBase {

    private final List<Punishment> kicked = new ArrayList<>();
    private ImportEnforcement enforcement;

    @BeforeEach
    void createEnforcement() {
        enforcement = new ImportEnforcement(plugin, cache, kicked::add);
    }

    private static Punishment punishment(UUID target, PunishmentType type, Instant expiry, boolean revoked) {
        Instant now = Instant.now();
        return new Punishment(
                UUID.randomUUID(),
                target,
                type,
                new PunishmentIssuer(VanillaBanParser.CONSOLE_ID, now.minusSeconds(60)),
                "imported",
                expiry,
                revoked ? new Revocation(VanillaBanParser.CONSOLE_ID, null, now) : null);
    }

    @Test
    void activeMuteIsCachedForOnlinePlayer() {
        UUID target = server.addPlayer("Target").getUniqueId();

        enforcement.accept(punishment(target, PunishmentType.MUTE, null, false));
        server.getScheduler().performOneTick();

        assertTrue(cache.find(target, PunishmentType.MUTE).isPresent());
    }

    @Test
    void muteForOfflinePlayerIsNotCached() {
        UUID target = UUID.randomUUID();

        enforcement.accept(punishment(target, PunishmentType.MUTE, null, false));
        server.getScheduler().performOneTick();

        assertTrue(cache.find(target, PunishmentType.MUTE).isEmpty());
    }

    @Test
    void revokedAndExpiredMutesAreNotCached() {
        UUID target = server.addPlayer("Target").getUniqueId();

        enforcement.accept(punishment(target, PunishmentType.MUTE, null, true));
        enforcement.accept(punishment(target, PunishmentType.MUTE, Instant.now().minus(Duration.ofHours(1)), false));
        server.getScheduler().performOneTick();

        assertTrue(cache.find(target, PunishmentType.MUTE).isEmpty());
    }

    @Test
    void activeBanIsKicked() {
        Punishment ban = punishment(UUID.randomUUID(), PunishmentType.BAN, null, false);

        enforcement.accept(ban);

        assertEquals(List.of(ban), kicked);
    }

    @Test
    void revokedAndExpiredBansAreNotKicked() {
        UUID target = UUID.randomUUID();

        enforcement.accept(punishment(target, PunishmentType.BAN, null, true));
        enforcement.accept(punishment(target, PunishmentType.BAN, Instant.now().minus(Duration.ofHours(1)), false));

        assertTrue(kicked.isEmpty());
    }

    @Test
    void warningsAndKicksTriggerNothing() {
        UUID target = server.addPlayer("Target").getUniqueId();

        enforcement.accept(punishment(target, PunishmentType.WARN, null, false));
        enforcement.accept(punishment(target, PunishmentType.KICK, null, false));
        server.getScheduler().performOneTick();

        assertTrue(kicked.isEmpty());
        assertTrue(cache.find(target, PunishmentType.MUTE).isEmpty());
    }
}
