package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PunishmentCommandsTest extends CommandTestBase {

    private PlayerMock staff;
    private PlayerMock target;

    @BeforeEach
    void registerCommands() {
        new PunishmentCommands(audiences, repository, config).register(manager);
        staff = server.addPlayer("Staff");
        target = server.addPlayer("Target");
    }

    private Punishment store(PunishmentType type, Instant expiry) {
        var punishment = new Punishment(
                UUID.randomUUID(),
                target.getUniqueId(),
                type,
                new PunishmentIssuer(staff.getUniqueId(), Instant.now()),
                "reason",
                expiry,
                false);
        repository.create(punishment);
        return punishment;
    }

    @Test
    void historyForPlayerWithoutPunishmentsRuns() {
        assertDoesNotThrow(() -> run(staff, "punish history Target"));
    }

    @Test
    void historyWithActiveRevokedAndExpiredEntriesRuns() {
        store(PunishmentType.WARN, null);
        store(PunishmentType.KICK, null);
        store(PunishmentType.MUTE, Instant.now().minus(Duration.ofHours(1)));
        Punishment ban = store(PunishmentType.BAN, null);
        repository.revoke(ban.id(), staff.getUniqueId(), "appeal", Instant.now());

        assertDoesNotThrow(() -> run(staff, "punish history Target"));
    }

    @Test
    void historyDoesNotModifyStoredPunishments() {
        store(PunishmentType.WARN, null);

        run(staff, "punish history Target");

        assertEquals(1, history(target.getUniqueId()).size());
    }
}
