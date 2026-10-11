package com.bradenkennedy.punishment.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.HistoryPage;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
                null);
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
    void findsActiveTimedPunishmentBeforeExpiry() {
        Punishment ban = punish(PunishmentType.BAN, Instant.now().plus(Duration.ofHours(1)));
        assertEquals(
                ban.id(),
                repository.findActive(player, PunishmentType.BAN).orElseThrow().id());
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
    void revokingUnknownPunishmentChangesNothing() {
        Punishment ban = punish(PunishmentType.BAN, null);
        repository.revoke(UUID.randomUUID(), UUID.randomUUID(), "appeal", Instant.now());

        assertEquals(
                List.of(ban.id()),
                repository.findHistory(player).stream().map(Punishment::id).toList());
        assertTrue(repository.findActive(player, PunishmentType.BAN).isPresent());
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

    private Punishment punishAt(PunishmentType type, Instant issuedAt) {
        Punishment punishment = new Punishment(
                UUID.randomUUID(),
                player,
                type,
                new PunishmentIssuer(UUID.randomUUID(), issuedAt),
                "reason",
                null,
                null);
        repository.create(punishment);
        return punishment;
    }

    private static List<UUID> ids(HistoryPage page) {
        return page.entries().stream().map(Punishment::id).toList();
    }

    @Test
    void pagedHistoryIsNewestFirst() {
        Instant base = Instant.now();
        Punishment oldest = punishAt(PunishmentType.WARN, base.minusSeconds(30));
        Punishment newest = punishAt(PunishmentType.WARN, base);
        Punishment middle = punishAt(PunishmentType.WARN, base.minusSeconds(10));

        assertEquals(
                List.of(newest.id(), middle.id(), oldest.id()),
                ids(repository.findHistory(player, Optional.empty(), 0, 10)));
    }

    @Test
    void pagedHistoryWithSameTimestampNeitherRepeatsNorSkips() {
        Instant at = Instant.now();
        Set<UUID> issued = new HashSet<>();
        for (int i = 0; i < 3; i++) issued.add(punishAt(PunishmentType.WARN, at).id());

        List<UUID> paged = new ArrayList<>();
        for (int offset = 0; offset < 3; offset++)
            paged.addAll(ids(repository.findHistory(player, Optional.empty(), offset, 1)));

        assertEquals(issued, new HashSet<>(paged));
        assertEquals(3, paged.size());
        assertEquals(ids(repository.findHistory(player, Optional.empty(), 0, 3)), paged);
    }

    @Test
    void pagedHistorySlicesByOffsetAndLimit() {
        Instant base = Instant.now();
        List<Punishment> warnings = new ArrayList<>();
        for (int i = 0; i < 5; i++) warnings.add(punishAt(PunishmentType.WARN, base.minusSeconds(i)));

        HistoryPage page = repository.findHistory(player, Optional.empty(), 2, 2);
        assertEquals(List.of(warnings.get(2).id(), warnings.get(3).id()), ids(page));
        assertEquals(5, page.total());
    }

    @Test
    void pagedHistoryOffsetPastEndKeepsTotal() {
        punishAt(PunishmentType.WARN, Instant.now());
        punishAt(PunishmentType.WARN, Instant.now());

        HistoryPage page = repository.findHistory(player, Optional.empty(), 10, 5);
        assertTrue(page.entries().isEmpty());
        assertEquals(2, page.total());
    }

    @Test
    void pagedHistoryFiltersByTypeAndCountsOnlyMatches() {
        punishAt(PunishmentType.WARN, Instant.now());
        punishAt(PunishmentType.KICK, Instant.now());
        Punishment kick = punishAt(PunishmentType.KICK, Instant.now().plusSeconds(1));

        HistoryPage page = repository.findHistory(player, Optional.of(PunishmentType.KICK), 0, 1);
        assertEquals(List.of(kick.id()), ids(page));
        assertEquals(2, page.total());
    }

    @Test
    void pagedHistoryIgnoresOtherPlayers() {
        punishAt(PunishmentType.WARN, Instant.now());
        assertEquals(
                0,
                repository
                        .findHistory(UUID.randomUUID(), Optional.empty(), 0, 10)
                        .total());
    }

    @Test
    void pagedHistoryIsEmptyForUnknownPlayer() {
        HistoryPage page = repository.findHistory(UUID.randomUUID(), Optional.empty(), 0, 10);
        assertTrue(page.entries().isEmpty());
        assertEquals(0, page.total());
    }

    @Test
    void pagedHistoryCarriesRevocationDetails() {
        Punishment ban = punish(PunishmentType.BAN, null);
        UUID revoker = UUID.randomUUID();
        Instant at = Instant.now().plusSeconds(5);
        repository.revoke(ban.id(), revoker, "appeal", at);
        punish(PunishmentType.WARN, null);

        List<Punishment> entries = repository
                .findHistory(player, Optional.of(PunishmentType.BAN), 0, 10)
                .entries();
        var revocation = entries.getFirst().revocation();
        assertEquals(revoker, revocation.by());
        assertEquals("appeal", revocation.reason());
        assertEquals(at.toEpochMilli(), revocation.at().toEpochMilli());
    }

    @Test
    void pagedHistoryHasNoRevocationForUnrevokedPunishment() {
        punish(PunishmentType.WARN, null);
        assertNull(repository
                .findHistory(player, Optional.empty(), 0, 10)
                .entries()
                .getFirst()
                .revocation());
    }

    @Test
    void pagedHistoryRejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> repository.findHistory(player, Optional.empty(), -1, 10));
        assertThrows(IllegalArgumentException.class, () -> repository.findHistory(player, Optional.empty(), 0, 0));
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
