package com.bradenkennedy.punishment.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.migration.ImportStore;
import com.bradenkennedy.punishment.network.NetworkCache;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class JdbcPunishmentRepositoryTest {
    private String database() {
        return "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    private Punishment punishment(UUID target, PunishmentType type) {
        return new Punishment(
                UUID.randomUUID(),
                target,
                type,
                new PunishmentIssuer(UUID.randomUUID(), Instant.ofEpochMilli(1700000000000L)),
                "reason",
                null,
                null);
    }

    @Test
    void twoNodesSeeBansMutesRevocationsAndHistoryPages() throws Exception {
        String url = database();
        try (var first = new JdbcPunishmentRepository(url, "", "");
                var second = new JdbcPunishmentRepository(url, "", "")) {
            UUID target = UUID.randomUUID();
            var ban = punishment(target, PunishmentType.BAN);
            var mute = punishment(target, PunishmentType.MUTE);
            var cache = new NetworkCache(second);
            cache.load(target);
            first.create(ban);
            first.create(mute);
            var events = new ArrayList<UUID>();
            cache.poll(c -> events.add(c.id()));
            assertEquals(ban, cache.active(target, PunishmentType.BAN).orElseThrow());
            assertEquals(mute, cache.active(target, PunishmentType.MUTE).orElseThrow());
            first.revoke(mute.id(), ban.issuer().issuer(), "Appeal", Instant.ofEpochMilli(1700000200000L));
            cache.poll(c -> events.add(c.id()));
            assertTrue(cache.active(target, PunishmentType.MUTE).isEmpty());
            assertEquals(
                    "Appeal",
                    second.findById(mute.id()).orElseThrow().revocation().reason());
            var page = second.findHistory(target, Optional.of(PunishmentType.BAN), 0, 1);
            assertEquals(1, page.total());
            assertEquals(List.of(ban), page.entries());
            assertEquals(3, events.size());
            cache.poll(c -> events.add(c.id()));
            assertEquals(3, events.size());
        }
    }

    @Test
    void importUsesTheSameSharedHistoryAndPreservesRevocation() throws Exception {
        String url = database();
        try (var repository = new JdbcPunishmentRepository(url, "", "");
                var store = new ImportStore(url, "", "", false)) {
            var source = punishment(UUID.randomUUID(), PunishmentType.BAN);
            var record = new Punishment(
                    source.id(),
                    source.target(),
                    source.type(),
                    source.issuer(),
                    source.reason(),
                    null,
                    new Revocation(source.issuer().issuer(), "Appealed", Instant.ofEpochMilli(1700000100000L)));
            assertTrue(store.store(record, "vanilla", "source-id", "Staff", "Player", false));
            assertEquals(record, repository.findById(record.id()).orElseThrow());
            assertFalse(store.store(record, "vanilla", "source-id", "Staff", "Player", false));
        }
    }

    @Test
    void failedChangeWriteRollsBackPunishmentAndConnectionCanBeRetried() throws Exception {
        String url = database();
        try (var repository = new JdbcPunishmentRepository(url, "", "");
                var connection = DriverManager.getConnection(url)) {
            try (var statement = connection.prepareStatement(
                    "ALTER TABLE punishment_changes ADD CONSTRAINT reject_changes CHECK (action='NEVER')")) {
                statement.execute();
            }
            var record = punishment(UUID.randomUUID(), PunishmentType.BAN);
            assertThrows(IllegalStateException.class, () -> repository.create(record));
            assertTrue(repository.findById(record.id()).isEmpty());
            try (var statement =
                    connection.prepareStatement("ALTER TABLE punishment_changes DROP CONSTRAINT reject_changes")) {
                statement.execute();
            }
            repository.create(record);
            assertEquals(record, repository.findById(record.id()).orElseThrow());
        }
    }

    @Test
    void warningsAcknowledgementExpiryAndPlayerNamesPersist() throws Exception {
        String url = database();
        try (var repository = new JdbcPunishmentRepository(url, "", "");
                var connection = DriverManager.getConnection(url)) {
            var warning = punishment(UUID.randomUUID(), PunishmentType.WARN);
            repository.create(warning);
            assertEquals(List.of(warning), repository.findUnacknowledgedWarnings(warning.target()));
            repository.acknowledge(warning.id());
            assertTrue(repository.findUnacknowledgedWarnings(warning.target()).isEmpty());
            repository.rememberName(warning.target(), "OldName");
            repository.rememberName(warning.target(), "NewName");
            try (var query = connection.prepareStatement("SELECT name FROM punishment_players WHERE id=?")) {
                query.setString(1, warning.target().toString());
                try (var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("NewName", rows.getString(1));
                }
            }
            var expired = new Punishment(
                    UUID.randomUUID(),
                    warning.target(),
                    PunishmentType.BAN,
                    warning.issuer(),
                    "expired",
                    Instant.EPOCH,
                    null);
            repository.create(expired);
            assertTrue(
                    repository.findActive(warning.target(), PunishmentType.BAN).isEmpty());
            assertEquals(2, repository.findHistory(warning.target()).size());
        }
    }

    @Test
    void delayedEventsSurviveClockSkewAndOldKicksAreNotReplayed() throws Exception {
        String url = database();
        try (var repository = new JdbcPunishmentRepository(url, "", "");
                var connection = DriverManager.getConnection(url)) {
            repository.create(punishment(UUID.randomUUID(), PunishmentType.KICK));
            var cache = new NetworkCache(repository);
            var received = new ArrayList<UUID>();
            cache.poll(c -> received.add(c.id()));
            assertTrue(received.isEmpty());
            var id = UUID.randomUUID();
            try (var insert = connection.prepareStatement(
                    "INSERT INTO punishment_changes(id,punishmentId,target,action,created) VALUES(?,?,?,?,?)")) {
                insert.setString(1, id.toString());
                insert.setString(2, UUID.randomUUID().toString());
                insert.setString(3, UUID.randomUUID().toString());
                insert.setString(4, "ISSUE");
                insert.setLong(5, 1);
                insert.executeUpdate();
            }
            cache.poll(c -> received.add(c.id()));
            cache.poll(c -> received.add(c.id()));
            assertEquals(List.of(id), received);
        }
    }
}
