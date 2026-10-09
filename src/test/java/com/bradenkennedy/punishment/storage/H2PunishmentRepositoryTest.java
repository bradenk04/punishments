package com.bradenkennedy.punishment.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    private Punishment newPunishment(PunishmentType type, Instant expiry) {
        return new Punishment(
                UUID.randomUUID(),
                player,
                type,
                new PunishmentIssuer(UUID.randomUUID(), Instant.now()),
                "reason",
                expiry,
                false);
    }

    private Punishment punish(PunishmentType type, Instant expiry) {
        Punishment punishment = newPunishment(type, expiry);
        repository.create(punishment);
        return punishment;
    }

    private long activeCount(PunishmentType type) {
        return repository.findHistory(player).stream()
                .filter(p -> p.type() == type && !p.revoked() && !p.expired())
                .count();
    }

    @Test
    void closedConnectionSurfacesEveryOperationFailure() throws Exception {
        Punishment warning = punish(PunishmentType.WARN, null);
        repository.connectionSource.close();
        List<Runnable> operations = List.of(
                () -> repository.create(newPunishment(PunishmentType.WARN, null)),
                () -> repository.revoke(warning.id(), UUID.randomUUID(), "appeal", Instant.now()),
                () -> repository.findActive(player, PunishmentType.BAN),
                () -> repository.findUnacknowledgedWarnings(player),
                () -> repository.acknowledge(warning.id()),
                () -> repository.findHistory(player));
        for (Runnable operation : operations) {
            StorageException failure = assertThrows(StorageException.class, operation::run);
            assertInstanceOf(SQLException.class, failure.getCause());
        }
    }

    @Test
    void repositoriesKeepTheirOwnDatabase(@TempDir File otherFolder) throws Exception {
        var other = new H2PunishmentRepository(otherFolder);
        try {
            punish(PunishmentType.WARN, null);
            assertTrue(other.findHistory(player).isEmpty());
            assertEquals(1, repository.findHistory(player).size());
        } finally {
            other.connectionSource.close();
        }
        assertEquals(1, repository.findHistory(player).size());
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

    @Test
    void concurrentBansResultInExactlyOneActiveBan() throws Exception {
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    // Same check-then-create sequence as TimedPunishmentCommands.issue
                    if (repository.findActive(player, PunishmentType.BAN).isEmpty()) {
                        repository.create(newPunishment(PunishmentType.BAN, null));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        assertEquals(1, activeCount(PunishmentType.BAN));
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void revokingLeavesNoActivePunishmentsOfThatType(PunishmentType type) {
        punish(type, null);
        punish(type, null);

        // Same lookup-then-revoke sequence as TimedPunishmentCommands.revoke
        repository
                .findActive(player, type)
                .ifPresent(active -> repository.revoke(active.id(), UUID.randomUUID(), "appeal", Instant.now()));

        assertTrue(repository.findActive(player, type).isEmpty());
        assertEquals(0, activeCount(type));
    }
}
