package com.bradenkennedy.punishment.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiteBansReaderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID STAFF = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static ImportBatch read() throws Exception {
        String script = new String(
                LiteBansReaderTest.class
                        .getResourceAsStream("/import/litebans/schema.sql")
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID())) {
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.split("\n")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
            return LiteBansReader.read(new LiteBansDatabase(connection, "litebans_"), NOW, Logger.getAnonymousLogger());
        }
    }

    private static Punishment byTarget(ImportBatch batch, String target) {
        return batch.records().stream()
                .map(ImportRecord::punishment)
                .filter(p -> p.target().toString().startsWith(target))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void countsSkippedAndFailedRows() throws Exception {
        ImportBatch batch = read();

        assertEquals(6, batch.records().size());
        assertEquals(1, batch.skipped());
        assertEquals(1, batch.failed());
    }

    @Test
    void activePermanentBanWithConsoleIssuer() throws Exception {
        Punishment ban = byTarget(read(), "aaaaaaaa");

        assertEquals(PunishmentType.BAN, ban.type());
        assertEquals("Griefing", ban.reason());
        assertEquals(VanillaBanParser.CONSOLE_ID, ban.issuer().issuer());
        assertEquals(Instant.ofEpochMilli(1700000000000L), ban.issuer().issuedAt());
        assertNull(ban.expiry());
        assertFalse(ban.revoked());
    }

    @Test
    void removedBanKeepsRevocationDetails() throws Exception {
        Punishment ban = byTarget(read(), "22222222");

        assertEquals(STAFF, ban.issuer().issuer());
        assertTrue(ban.revoked());
        assertEquals(STAFF, ban.revocation().by());
        assertEquals("appealed", ban.revocation().reason());
        assertEquals(Instant.parse("2024-02-01T00:00:00Z"), ban.revocation().at());
    }

    @Test
    void expiredBanIsNotRevoked() throws Exception {
        Punishment ban = byTarget(read(), "33333333");

        assertFalse(ban.revoked());
        assertTrue(ban.expired());
    }

    @Test
    void mapsMutesWarningsAndKicks() throws Exception {
        ImportBatch batch = read();

        assertEquals(PunishmentType.MUTE, byTarget(batch, "44444444").type());
        assertEquals(PunishmentType.WARN, byTarget(batch, "55555555").type());
        Punishment kick = byTarget(batch, "66666666");
        assertEquals(PunishmentType.KICK, kick.type());
        assertFalse(kick.revoked());
        assertNull(kick.expiry());
    }

    @Test
    void sourceIdsAreScopedByTable() throws Exception {
        assertTrue(read().records().stream().anyMatch(r -> r.sourceId().equals("bans:1")));
        assertTrue(read().records().stream().anyMatch(r -> r.sourceId().equals("mutes:1")));
    }

    @Test
    void unsupportedDriversAreRejected(@TempDir File folder) throws Exception {
        Files.writeString(new File(folder, "config.yml").toPath(), "sql:\n  driver: SQLite\n");

        assertThrows(SQLException.class, () -> LiteBansDatabase.open(folder, null));
    }

    @Test
    void unsafeTablePrefixIsRejected(@TempDir File folder) throws Exception {
        Files.writeString(
                new File(folder, "config.yml").toPath(),
                "sql:\n  driver: MariaDB\n  table_prefix: 'x; DROP TABLE y'\n");

        assertThrows(SQLException.class, () -> LiteBansDatabase.open(folder, null));
    }
}
