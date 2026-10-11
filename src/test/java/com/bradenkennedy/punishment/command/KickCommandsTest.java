package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.events.PlayerPunishedEvent;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class KickCommandsTest extends CommandTestBase {

    private PlayerMock staff;
    private PlayerMock target;

    @BeforeEach
    void registerCommands() {
        new KickCommands(plugin, support, config).register(manager);
        staff = server.addPlayer("Staff");
        target = server.addPlayer("Target");
    }

    @Test
    void kickStoresPunishmentAndRemovesPlayerAfterOneTick() {
        run(staff, "kick Target spamming");
        server.getScheduler().performOneTick();

        Punishment stored = history(target.getUniqueId(), PunishmentType.KICK).getFirst();
        assertEquals("spamming", stored.reason());
        assertEquals(staff.getUniqueId(), stored.issuer().issuer());
        assertFalse(target.isOnline());
    }

    @Test
    void kickWithoutReasonStoresNullReason() {
        run(staff, "kick Target");

        assertEquals(
                null,
                history(target.getUniqueId(), PunishmentType.KICK).getFirst().reason());
    }

    @Test
    void kickIsNotMutuallyExclusive() {
        run(staff, "kick Target one");
        run(staff, "kick Target two");

        assertEquals(2, history(target.getUniqueId(), PunishmentType.KICK).size());
    }

    @Test
    void exemptTargetIsNotKickedOrStored() {
        target.setOp(true);

        run(staff, "kick Target spamming");
        server.getScheduler().performOneTick();

        assertTrue(history(target.getUniqueId()).isEmpty());
        assertTrue(target.isOnline());
    }

    @Test
    void cancelledEventNeitherStoresNorKicks() {
        cancelEvents(PlayerPunishedEvent.class);

        run(staff, "kick Target spamming");
        server.getScheduler().performOneTick();

        assertTrue(history(target.getUniqueId()).isEmpty());
        assertTrue(target.isOnline());
    }
}
