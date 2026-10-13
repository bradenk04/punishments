package com.bradenkennedy.punishment.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.ServerTestBase;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class WarnListenerTest extends ServerTestBase {

    private WarnListener listener;
    private PlayerMock player;

    @BeforeEach
    void createListener() {
        listener = new WarnListener(repository, config, audiences);
        player = server.addPlayer("Target");
    }

    @Test
    void joiningDeliversAndAcknowledgesPendingWarnings() {
        store(player.getUniqueId(), PunishmentType.WARN, null);
        store(player.getUniqueId(), PunishmentType.WARN, null);
        assertEquals(
                2, repository.findUnacknowledgedWarnings(player.getUniqueId()).size());

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertTrue(repository.findUnacknowledgedWarnings(player.getUniqueId()).isEmpty());
    }

    @Test
    void deliveredWarningsAreNotDeliveredAgain() {
        store(player.getUniqueId(), PunishmentType.WARN, null);
        listener.deliverPending(player);
        store(player.getUniqueId(), PunishmentType.WARN, null);

        assertEquals(
                1, repository.findUnacknowledgedWarnings(player.getUniqueId()).size());
    }

    @Test
    void joiningWithoutWarningsChangesNothing() {
        store(player.getUniqueId(), PunishmentType.BAN, null);

        listener.deliverPending(player);

        assertEquals(1, history(player.getUniqueId()).size());
    }

    @Test
    void revokedWarningsAreNotDelivered() {
        var warning = store(player.getUniqueId(), PunishmentType.WARN, null);
        repository.revoke(warning.id(), UUID.randomUUID(), "mistake", Instant.now());

        assertTrue(repository.findUnacknowledgedWarnings(player.getUniqueId()).isEmpty());
    }

    @Test
    void onlyTheJoiningPlayersWarningsAreAcknowledged() {
        PlayerMock other = server.addPlayer("Other");
        store(other.getUniqueId(), PunishmentType.WARN, null);

        listener.deliverPending(player);

        assertEquals(
                1, repository.findUnacknowledgedWarnings(other.getUniqueId()).size());
    }
}
