package com.bradenkennedy.punishment.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.migration.ImportStore;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExternalDatabaseTest {
    @Test
    void mysqlSchemaAndOperations() throws Exception {
        exercise("MYSQL_JDBC");
    }

    @Test
    void postgresSchemaAndOperations() throws Exception {
        exercise("POSTGRES_JDBC");
    }

    private void exercise(String key) throws Exception {
        String url = System.getenv(key);
        assumeTrue(url != null && !url.isBlank(), "Set " + key + " for integration test");
        try (var repository = new JdbcPunishmentRepository(url, "punishments", "punishments")) {
            UUID target = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            if (repository.findById(id).isEmpty())
                repository.create(new Punishment(
                        id,
                        target,
                        PunishmentType.BAN,
                        new PunishmentIssuer(new UUID(0, 0), Instant.ofEpochMilli(1700000000000L)),
                        "Integration test",
                        null,
                        null));
            repository.rememberName(target, "OldName");
            repository.rememberName(target, "IntegrationPlayer");
            assertEquals(
                    id,
                    repository
                            .findActive(target, PunishmentType.BAN)
                            .orElseThrow()
                            .id());
            var other = new Punishment(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    PunishmentType.MUTE,
                    new PunishmentIssuer(new UUID(0, 0), Instant.ofEpochMilli(1700000100000L)),
                    "temporary",
                    null,
                    null);
            try (var store = new ImportStore(url, "punishments", "punishments", true)) {
                assertTrue(store.store(other, "vanilla", other.id().toString(), "Staff", "Player", true));
                assertTrue(repository.findById(other.id()).isEmpty());
            }
            try (var store = new ImportStore(url, "punishments", "punishments", false)) {
                assertTrue(store.store(other, "vanilla", other.id().toString(), "Staff", "Player", false));
                assertFalse(store.store(other, "vanilla", other.id().toString(), "Staff", "Player", false));
            }
            repository.revoke(other.id(), new UUID(0, 0), "test", Instant.ofEpochMilli(1700000200000L));
            assertEquals(
                    "test",
                    repository.findById(other.id()).orElseThrow().revocation().reason());
            assertEquals(
                    1,
                    repository
                            .findHistory(target, Optional.of(PunishmentType.BAN), 0, 1)
                            .total());
        }
    }
}
