package com.bradenkennedy.punishment.listener;

import static org.junit.jupiter.api.Assertions.*;

import com.bradenkennedy.punishment.ServerTestBase;
import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.network.NetworkCache;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkListenerTest extends ServerTestBase {
    @Test
    void remoteMuteAndUnmuteRefreshTheExistingServerCache() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var shared = new JdbcPunishmentRepository(url, "", "");
                var remote = new JdbcPunishmentRepository(url, "", "")) {
            var player = server.addPlayer("Target");
            var listener = new NetworkListener(
                    plugin,
                    shared,
                    new NetworkCache(shared),
                    cache,
                    new BanListener(plugin, shared, config),
                    config,
                    audiences);
            var mute = new Punishment(
                    UUID.randomUUID(),
                    player.getUniqueId(),
                    PunishmentType.MUTE,
                    new PunishmentIssuer(UUID.randomUUID(), Instant.ofEpochMilli(1700000000000L)),
                    "reason",
                    null,
                    null);
            remote.create(mute);
            listener.poll();
            assertEquals(
                    mute, cache.find(player.getUniqueId(), PunishmentType.MUTE).orElseThrow());
            remote.revoke(mute.id(), mute.issuer().issuer(), "appeal", Instant.now());
            listener.poll();
            assertTrue(cache.find(player.getUniqueId(), PunishmentType.MUTE).isEmpty());
        }
    }

    @Test
    void remoteKickRunsOnTheServerScheduler() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var shared = new JdbcPunishmentRepository(url, "", "");
                var remote = new JdbcPunishmentRepository(url, "", "")) {
            var player = server.addPlayer("Target");
            var listener = new NetworkListener(
                    plugin,
                    shared,
                    new NetworkCache(shared),
                    cache,
                    new BanListener(plugin, shared, config),
                    config,
                    audiences);
            remote.create(new Punishment(
                    UUID.randomUUID(),
                    player.getUniqueId(),
                    PunishmentType.KICK,
                    new PunishmentIssuer(UUID.randomUUID(), Instant.now()),
                    "reason",
                    null,
                    null));
            listener.poll();
            assertTrue(player.isOnline());
            server.getScheduler().performOneTick();
            assertFalse(player.isOnline());
        }
    }
}
