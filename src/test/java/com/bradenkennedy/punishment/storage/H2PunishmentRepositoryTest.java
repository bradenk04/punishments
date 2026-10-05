package com.bradenkennedy.punishment.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class H2PunishmentRepositoryTest {

    private final UUID player = UUID.randomUUID();
    private H2PunishmentRepository repository;

    @BeforeEach
    void setUp(@TempDir File dataFolder) throws Exception {
        repository = new H2PunishmentRepository(dataFolder);
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.connectionSource.close();
    }

    private Punishment punish(PunishmentType type, Instant expiry) {
        Punishment punishment = new Punishment(
                UUID.randomUUID(),
                player,
                type,
                new PunishmentIssuer(UUID.randomUUID(), Instant.now()),
                "reason",
                expiry,
                false);
        repository.create(punishment);
        return punishment;
    }

    @Test
    void findsActivePermanentPunishment() {
        Punishment ban = punish(PunishmentType.BAN, null);
        assertEquals(
                ban.id(),
                repository.findActive(player, PunishmentType.BAN).orElseThrow().id());
    }

    @Test
    void ignoresExpiredPunishment() {
        punish(PunishmentType.MUTE, Instant.now().minus(Duration.ofHours(1)));
        assertTrue(repository.findActive(player, PunishmentType.MUTE).isEmpty());
    }

    @Test
    void ignoresPunishmentOfOtherType() {
        punish(PunishmentType.MUTE, null);
        assertTrue(repository.findActive(player, PunishmentType.BAN).isEmpty());
    }

    @Test
    void revokedPunishmentIsNotActiveButRemainsInHistory() {
        Punishment ban = punish(PunishmentType.BAN, null);
        repository.revoke(ban.id(), UUID.randomUUID(), "appeal", Instant.now());

        assertTrue(repository.findActive(player, PunishmentType.BAN).isEmpty());
        assertTrue(repository.findHistory(player).getFirst().revoked());
    }

    @Test
    void acknowledgedWarningsAreNotReturned() {
        Punishment first = punish(PunishmentType.WARN, null);
        Punishment second = punish(PunishmentType.WARN, null);
        repository.acknowledge(first.id());

        List<Punishment> unacknowledged = repository.findUnacknowledgedWarnings(player);
        assertEquals(
                List.of(second.id()),
                unacknowledged.stream().map(Punishment::id).toList());
    }

    @Test
    void historyContainsAllPunishmentsForPlayer() {
        punish(PunishmentType.WARN, null);
        punish(PunishmentType.KICK, null);
        assertEquals(2, repository.findHistory(player).size());
    }

    @Test
    void historyIsEmptyForUnknownPlayer() {
        assertTrue(repository.findHistory(UUID.randomUUID()).isEmpty());
    }
}
