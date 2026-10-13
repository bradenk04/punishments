package com.bradenkennedy.punishment.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.ServerTestBase;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@SuppressWarnings("deprecation")
class BanListenerTest extends ServerTestBase {

    private BanListener listener;

    @BeforeEach
    void createListener() {
        listener = new BanListener(plugin, repository, config);
    }

    private AsyncPlayerPreLoginEvent preLogin(UUID uuid) {
        return new AsyncPlayerPreLoginEvent("Target", InetAddress.getLoopbackAddress(), uuid, false);
    }

    @Test
    void bannedPlayerIsDisallowedWithBanScreen() {
        UUID uuid = UUID.randomUUID();
        store(uuid, PunishmentType.BAN, null);
        var event = preLogin(uuid);

        listener.onPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, event.getLoginResult());
        assertTrue(event.getKickMessage().contains("griefing"));
    }

    @Test
    void temporarilyBannedPlayerIsDisallowed() {
        UUID uuid = UUID.randomUUID();
        store(uuid, PunishmentType.BAN, Instant.now().plus(Duration.ofHours(1)));
        var event = preLogin(uuid);

        listener.onPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, event.getLoginResult());
    }

    @Test
    void unpunishedPlayerIsAllowed() {
        var event = preLogin(UUID.randomUUID());

        listener.onPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
    }

    @Test
    void expiredBanIsAllowed() {
        UUID uuid = UUID.randomUUID();
        store(uuid, PunishmentType.BAN, Instant.now().minus(Duration.ofHours(1)));
        var event = preLogin(uuid);

        listener.onPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
    }

    @Test
    void revokedBanIsAllowed() {
        UUID uuid = UUID.randomUUID();
        var ban = store(uuid, PunishmentType.BAN, null);
        repository.revoke(ban.id(), UUID.randomUUID(), "appeal", Instant.now());
        var event = preLogin(uuid);

        listener.onPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
    }

    @Test
    void muteDoesNotBlockLogin() {
        UUID uuid = UUID.randomUUID();
        store(uuid, PunishmentType.MUTE, null);
        var event = preLogin(uuid);

        listener.onPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
    }

    @Test
    void kickRemovesBannedOnlinePlayerAfterOneTick() {
        PlayerMock target = server.addPlayer("Target");
        var ban = store(target.getUniqueId(), PunishmentType.BAN, null);

        listener.kick(ban);
        assertTrue(target.isOnline());
        server.getScheduler().performOneTick();

        assertFalse(target.isOnline());
    }

    @Test
    void kickOfOfflinePlayerDoesNothing() {
        var ban = store(UUID.randomUUID(), PunishmentType.BAN, null);

        listener.kick(ban);

        assertDoesNotThrow(() -> server.getScheduler().performOneTick());
    }
}
