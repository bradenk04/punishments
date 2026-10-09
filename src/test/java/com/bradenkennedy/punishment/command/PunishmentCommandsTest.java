package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PunishmentCommandsTest extends CommandTestBase {

    private PunishmentCommands commands;
    private PlayerMock staff;
    private PlayerMock target;

    @BeforeEach
    void registerCommands() {
        commands = new PunishmentCommands(audiences, repository, config);
        commands.register(manager);
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
                null);
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

    private Punishment storeAt(Instant issuedAt, String reason) {
        var punishment = new Punishment(
                UUID.randomUUID(),
                target.getUniqueId(),
                PunishmentType.WARN,
                new PunishmentIssuer(staff.getUniqueId(), issuedAt),
                reason,
                null,
                null);
        repository.create(punishment);
        return punishment;
    }

    private String text(int page) {
        return commands.historyMessages(target, page).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .collect(Collectors.joining("\n"));
    }

    private void storeWarnings(int count) {
        Instant base = Instant.now();
        for (int i = 0; i < count; i++) storeAt(base.minusSeconds(i), "reason-" + i);
    }

    @ParameterizedTest
    @CsvSource({"0,10,0", "1,10,1", "10,10,1", "11,10,2", "25,10,3", "5,1,5"})
    void pageCountRoundsUp(int total, int pageSize, int expected) {
        assertEquals(expected, PunishmentCommands.pageCount(total, pageSize));
    }

    @ParameterizedTest
    @CsvSource({"1,10,0", "2,10,10", "3,5,10", "2147483647,10,2147483647"})
    void offsetSkipsEarlierPagesWithoutOverflow(int page, int pageSize, int expected) {
        assertEquals(expected, PunishmentCommands.offset(page, pageSize));
    }

    @Test
    void firstPageShowsPageSizeEntriesNewestFirst() {
        storeWarnings(12);

        String text = text(1);
        assertTrue(text.contains("page 1/2"));
        assertTrue(text.contains("reason-0"));
        assertTrue(text.contains("reason-9"));
        assertTrue(!text.contains("reason-10"));
        assertTrue(text.indexOf("reason-0") < text.indexOf("reason-9"));
    }

    @Test
    void secondPageShowsRemainingEntries() {
        storeWarnings(12);

        String text = text(2);
        assertTrue(text.contains("page 2/2"));
        assertTrue(text.contains("reason-10"));
        assertTrue(text.contains("reason-11"));
        assertTrue(!text.contains("reason-9"));
    }

    @Test
    void pageBeyondTheEndShowsLastPage() {
        storeWarnings(12);

        assertTrue(text(99).contains("page 2/2"));
    }

    @Test
    void pageBelowOneIsRejected() {
        assertThrows(CompletionException.class, () -> run(staff, "punish history Target 0"));
    }

    @Test
    void emptyHistoryShowsEmptyMessage() {
        assertTrue(text(1).contains("No punishments found"));
    }

    @Test
    void pageSizeComesFromConfig() {
        plugin.getConfig().set("history.page-size", 2);
        storeWarnings(5);

        assertTrue(text(1).contains("page 1/3"));
    }

    @Test
    void consoleCanViewHistory() {
        storeWarnings(1);

        assertDoesNotThrow(() -> run(server.getConsoleSender(), "punish history Target 1"));
    }

    @Test
    void entryShowsStaffDurationAndStatus() {
        store(PunishmentType.BAN, Instant.now().plus(Duration.ofHours(2)));

        String text = text(1);
        assertTrue(text.contains("by Staff"));
        assertTrue(text.contains("2h"));
        assertTrue(text.contains("active"));
    }

    @Test
    void permanentEntryShowsPermanentDuration() {
        store(PunishmentType.WARN, null);

        assertTrue(text(1).contains("for ever"));
    }

    @Test
    void expiredEntryShowsExpiredAndNoRemaining() {
        store(PunishmentType.MUTE, Instant.now().minus(Duration.ofHours(1)));

        String text = text(1);
        assertTrue(text.contains("expired, - left"));
    }

    @Test
    void revokedEntryShowsRevokedEvenWhenNotExpired() {
        Punishment ban = store(PunishmentType.BAN, Instant.now().plus(Duration.ofHours(2)));
        repository.revoke(ban.id(), staff.getUniqueId(), "appeal", Instant.now());

        assertTrue(text(1).contains("revoked, - left"));
    }
}
