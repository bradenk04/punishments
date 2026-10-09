package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.events.PlayerPunishedEvent;
import com.bradenkennedy.punishment.api.events.PlayerPunishmentRevokedEvent;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TimedPunishmentCommandsTest extends CommandTestBase {

    private final List<Punishment> enforced = new ArrayList<>();
    private PlayerMock staff;
    private PlayerMock target;

    @BeforeEach
    void registerCommands() {
        for (PunishmentType type : List.of(PunishmentType.BAN, PunishmentType.MUTE)) {
            new TimedPunishmentCommands(support, repository, config).register(manager, type, enforced::add);
        }
        staff = server.addPlayer("Staff");
        target = server.addPlayer("Target");
    }

    private static String name(PunishmentType type) {
        return type.name().toLowerCase();
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void permanentPunishmentIsStoredAndEnforced(PunishmentType type) {
        run(staff, name(type) + " Target griefing");

        Punishment stored = history(target.getUniqueId(), type).getFirst();
        assertEquals("griefing", stored.reason());
        assertEquals(staff.getUniqueId(), stored.issuer().issuer());
        assertEquals(null, stored.expiry());
        assertFalse(stored.revoked());
        assertEquals(List.of(stored.id()), enforced.stream().map(Punishment::id).toList());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void punishmentWithoutReasonIsStoredWithNullReason(PunishmentType type) {
        run(staff, name(type) + " Target");

        assertEquals(null, history(target.getUniqueId(), type).getFirst().reason());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void temporaryPunishmentExpiresAfterDuration(PunishmentType type) {
        Instant before = Instant.now();

        run(staff, "temp" + name(type) + " Target 1h spamming");

        Punishment stored = history(target.getUniqueId(), type).getFirst();
        assertEquals("spamming", stored.reason());
        assertFalse(stored.expiry().isBefore(before.plus(Duration.ofHours(1))));
        assertTrue(stored.expiry().isBefore(Instant.now().plus(Duration.ofHours(1))));
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void secondPunishmentOfSameTypeIsRefused(PunishmentType type) {
        run(staff, name(type) + " Target first");
        run(staff, name(type) + " Target second");

        assertEquals(1, history(target.getUniqueId(), type).size());
        assertEquals(1, enforced.size());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void banAndMuteAreIndependent(PunishmentType type) {
        PunishmentType other = type == PunishmentType.BAN ? PunishmentType.MUTE : PunishmentType.BAN;

        run(staff, name(type) + " Target one");
        run(staff, name(other) + " Target two");

        assertEquals(1, history(target.getUniqueId(), type).size());
        assertEquals(1, history(target.getUniqueId(), other).size());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void operatorTargetIsExempt(PunishmentType type) {
        target.setOp(true);

        run(staff, name(type) + " Target griefing");

        assertTrue(history(target.getUniqueId()).isEmpty());
        assertTrue(enforced.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void targetWithExemptPermissionIsExempt(PunishmentType type) {
        target.addAttachment(plugin, "punishments.exempt", true);

        run(staff, name(type) + " Target griefing");

        assertTrue(history(target.getUniqueId()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void cancelledPunishEventStoresNothing(PunishmentType type) {
        cancelEvents(PlayerPunishedEvent.class);

        run(staff, name(type) + " Target griefing");

        assertTrue(history(target.getUniqueId()).isEmpty());
        assertTrue(enforced.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void consoleIssuerIsZeroUuid(PunishmentType type) {
        run(server.getConsoleSender(), name(type) + " Target griefing");

        assertEquals(
                new UUID(0, 0),
                history(target.getUniqueId(), type).getFirst().issuer().issuer());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void revokeEndsActivePunishmentAndRecordsRevoker(PunishmentType type) throws Exception {
        run(staff, name(type) + " Target griefing");
        Punishment active = history(target.getUniqueId(), type).getFirst();

        run(staff, "un" + name(type) + " Target appealed");

        assertTrue(repository.findActive(target.getUniqueId(), type).isEmpty());
        var model = storedModel(active.id());
        assertTrue(model.isRevoked());
        assertEquals(staff.getUniqueId(), model.getRevokedBy());
        assertEquals("appealed", model.getRevokedReason());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void revokeWithoutActivePunishmentChangesNothing(PunishmentType type) {
        run(staff, "un" + name(type) + " Target");

        assertTrue(history(target.getUniqueId()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void cancelledRevokeEventLeavesPunishmentActive(PunishmentType type) {
        run(staff, name(type) + " Target griefing");
        cancelEvents(PlayerPunishmentRevokedEvent.class);

        run(staff, "un" + name(type) + " Target");

        assertTrue(repository.findActive(target.getUniqueId(), type).isPresent());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void revokedPunishmentCanBeIssuedAgain(PunishmentType type) {
        run(staff, name(type) + " Target first");
        run(staff, "un" + name(type) + " Target");

        run(staff, name(type) + " Target second");

        assertEquals(2, history(target.getUniqueId(), type).size());
        assertTrue(repository.findActive(target.getUniqueId(), type).isPresent());
    }
}
