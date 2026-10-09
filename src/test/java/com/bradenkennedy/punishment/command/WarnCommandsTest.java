package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.events.PlayerPunishedEvent;
import com.bradenkennedy.punishment.api.events.PlayerPunishmentRevokedEvent;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.listener.WarnListener;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class WarnCommandsTest extends CommandTestBase {

    private PlayerMock staff;
    private PlayerMock target;

    @BeforeEach
    void registerCommands() {
        var warnListener = new WarnListener(repository, config, audiences);
        new WarnCommands(support, repository, warnListener).register(manager);
        staff = server.addPlayer("Staff");
        target = server.addPlayer("Target");
    }

    private long unrevokedWarnings(UUID player) {
        return history(player, PunishmentType.WARN).stream()
                .filter(p -> !p.revoked())
                .count();
    }

    @Test
    void warnStoresPunishmentWithReason() {
        run(staff, "warn Target be nice");

        Punishment stored = history(target.getUniqueId(), PunishmentType.WARN).getFirst();
        assertEquals("be nice", stored.reason());
        assertEquals(staff.getUniqueId(), stored.issuer().issuer());
        assertEquals(null, stored.expiry());
    }

    @Test
    void warningsStack() {
        run(staff, "warn Target one");
        run(staff, "warn Target two");

        assertEquals(2, history(target.getUniqueId(), PunishmentType.WARN).size());
    }

    @Test
    void onlineTargetHasWarningAcknowledgedImmediately() {
        run(staff, "warn Target be nice");

        assertTrue(repository.findUnacknowledgedWarnings(target.getUniqueId()).isEmpty());
    }

    @Test
    void offlineTargetKeepsWarningUnacknowledged() {
        PlayerMock ghost = server.addPlayer("Ghost");
        ghost.disconnect();

        run(staff, "warn Ghost be nice");

        assertEquals(
                1, repository.findUnacknowledgedWarnings(ghost.getUniqueId()).size());
    }

    @Test
    void cancelledWarnEventStoresNothing() {
        cancelEvents(PlayerPunishedEvent.class);

        run(staff, "warn Target be nice");

        assertTrue(history(target.getUniqueId()).isEmpty());
    }

    @Test
    void unwarnRevokesMostRecentActiveWarning() {
        run(staff, "warn Target first");
        run(staff, "warn Target second");

        run(staff, "unwarn Target");

        var warnings = history(target.getUniqueId(), PunishmentType.WARN);
        assertFalse(warnings.stream()
                .filter(p -> p.reason().equals("first"))
                .findFirst()
                .orElseThrow()
                .revoked());
        assertTrue(warnings.stream()
                .filter(p -> p.reason().equals("second"))
                .findFirst()
                .orElseThrow()
                .revoked());
    }

    @Test
    void unwarnWithIdRevokesThatWarning() throws Exception {
        run(staff, "warn Target first");
        run(staff, "warn Target second");
        Punishment first = history(target.getUniqueId(), PunishmentType.WARN).stream()
                .filter(p -> p.reason().equals("first"))
                .findFirst()
                .orElseThrow();

        run(staff, "unwarn Target " + first.id() + " mistake");

        assertTrue(storedModel(first.id()).isRevoked());
        assertEquals("mistake", storedModel(first.id()).getRevokedReason());
        assertEquals(1, unrevokedWarnings(target.getUniqueId()));
    }

    @Test
    void unwarnSkipsAlreadyRevokedWarnings() {
        run(staff, "warn Target first");
        run(staff, "warn Target second");
        run(staff, "unwarn Target");

        run(staff, "unwarn Target");

        assertEquals(0, unrevokedWarnings(target.getUniqueId()));
    }

    @Test
    void unwarnWithoutWarningsChangesNothing() {
        run(staff, "unwarn Target");

        assertTrue(history(target.getUniqueId()).isEmpty());
    }

    @Test
    void unwarnWithUnknownIdChangesNothing() {
        run(staff, "warn Target first");

        run(staff, "unwarn Target " + UUID.randomUUID());

        assertEquals(1, unrevokedWarnings(target.getUniqueId()));
    }

    @Test
    void cancelledRevokeEventKeepsWarningActive() {
        run(staff, "warn Target first");
        cancelEvents(PlayerPunishmentRevokedEvent.class);

        run(staff, "unwarn Target");

        assertEquals(1, unrevokedWarnings(target.getUniqueId()));
    }
}
