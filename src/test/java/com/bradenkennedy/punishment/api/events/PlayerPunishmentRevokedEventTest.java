package com.bradenkennedy.punishment.api.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PlayerPunishmentRevokedEventTest {

    private final Punishment punishment = new Punishment(
            UUID.randomUUID(),
            UUID.randomUUID(),
            PunishmentType.BAN,
            new PunishmentIssuer(UUID.randomUUID(), Instant.now()),
            "reason",
            null,
            false);

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void exposesPunishment() {
        assertSame(punishment, new PlayerPunishmentRevokedEvent(punishment).getPunishment());
    }

    @Test
    void isNotCancelledByDefaultAndCanBeToggled() {
        PlayerPunishmentRevokedEvent event = new PlayerPunishmentRevokedEvent(punishment);
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());

        event.setCancelled(false);
        assertFalse(event.isCancelled());
    }

    @Test
    void isSynchronousOnPrimaryThread() {
        assertFalse(new PlayerPunishmentRevokedEvent(punishment).isAsynchronous());
    }

    @Test
    void isAsynchronousOffPrimaryThread() throws InterruptedException {
        AtomicBoolean asynchronous = new AtomicBoolean();
        Thread thread =
                new Thread(() -> asynchronous.set(new PlayerPunishmentRevokedEvent(punishment).isAsynchronous()));
        thread.start();
        thread.join();

        assertTrue(asynchronous.get());
    }

    @Test
    void handlersMatchStaticHandlerList() {
        assertSame(
                PlayerPunishmentRevokedEvent.getHandlerList(),
                new PlayerPunishmentRevokedEvent(punishment).getHandlers());
    }

    @Test
    void registeredListenerReceivesAndCancelsEvent() {
        AtomicReference<Punishment> received = new AtomicReference<>();
        server.getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            void on(PlayerPunishmentRevokedEvent event) {
                                received.set(event.getPunishment());
                                event.setCancelled(true);
                            }
                        },
                        MockBukkit.createMockPlugin());
        PlayerPunishmentRevokedEvent event = new PlayerPunishmentRevokedEvent(punishment);

        server.getPluginManager().callEvent(event);

        assertEquals(punishment, received.get());
        assertTrue(event.isCancelled());
    }
}
