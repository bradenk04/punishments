package com.lotusplugins.warrant.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentType;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibertyBansReaderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID STAFF = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static Connection fixtureConnection() throws Exception {
        String script = new String(
                LibertyBansReaderTest.class
                        .getResourceAsStream("/import/libertybans/schema.sql")
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.split("\n")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
        return connection;
    }

    private static ImportBatch read(Logger logger) throws Exception {
        try (Connection connection = fixtureConnection()) {
            return LibertyBansReader.read(connection, NOW, logger);
        }
    }

    private static ImportBatch read() throws Exception {
        return read(Logger.getAnonymousLogger());
    }

    private static Punishment byTarget(ImportBatch batch, String prefix) {
        return batch.records().stream()
                .map(ImportRecord::punishment)
                .filter(p -> p.target().toString().startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void countsSkippedAndFailedRows() throws Exception {
        ImportBatch batch = read();

        assertEquals(5, batch.records().size());
        assertEquals(2, batch.skipped());
        assertEquals(1, batch.failed());
    }

    @Test
    void activePermanentBanKeepsOperatorAndTimes() throws Exception {
        Punishment ban = byTarget(read(), "11111111");

        assertEquals(PunishmentType.BAN, ban.type());
        assertEquals("Griefing", ban.reason());
        assertEquals(STAFF, ban.issuer().issuer());
        assertEquals(Instant.ofEpochSecond(1700000000L), ban.issuer().issuedAt());
        assertNull(ban.expiry());
        assertFalse(ban.revoked());
    }

    @Test
    void removedUnexpiredMuteIsRevokedAndConsoleOperatorIsPreserved() throws Exception {
        Punishment mute = byTarget(read(), "22222222");

        assertEquals(PunishmentType.MUTE, mute.type());
        assertTrue(mute.revoked());
        assertEquals(VanillaBanParser.CONSOLE_ID, mute.issuer().issuer());
        assertEquals(Instant.ofEpochSecond(4102444800L), mute.expiry());
    }

    @Test
    void expiredWarningIsNotRevoked() throws Exception {
        Punishment warning = byTarget(read(), "33333333");

        assertEquals(PunishmentType.WARN, warning.type());
        assertFalse(warning.revoked());
        assertTrue(warning.expired());
    }

    @Test
    void kicksAreNeverRevokedOrTimed() throws Exception {
        Punishment kick = byTarget(read(), "44444444");

        assertEquals(PunishmentType.KICK, kick.type());
        assertFalse(kick.revoked());
        assertNull(kick.expiry());
    }

    @Test
    void compositeVictimWithPlayerUuidIsImported() throws Exception {
        assertEquals(PunishmentType.BAN, byTarget(read(), "77777777").type());
    }

    @Test
    void sourceIdsArePunishmentIds() throws Exception {
        assertEquals("1", read().records().getFirst().sourceId());
    }

    @Test
    void readsBinaryUuidsAsUsedByMariaDb() throws Exception {
        UUID victim = UUID.fromString("55555555-5555-5555-5555-555555555555");
        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "")) {
            try (Statement statement = connection.createStatement()) {
                for (String table : List.of("libertybans_simple_history", "libertybans_simple_active")) {
                    statement.execute("CREATE TABLE \"" + table + "\" (\"id\" BIGINT, \"type\" SMALLINT,"
                            + " \"victim_type\" SMALLINT, \"victim_uuid\" BINARY(16), \"operator\" BINARY(16),"
                            + " \"reason\" VARCHAR(256), \"start\" BIGINT, \"end\" BIGINT)");
                }
            }
            byte[] id = ByteBuffer.allocate(16)
                    .putLong(victim.getMostSignificantBits())
                    .putLong(victim.getLeastSignificantBits())
                    .array();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO \"libertybans_simple_history\" VALUES (1, 0, 0, ?, ?, 'Binary', 1700000000, 0)")) {
                insert.setBytes(1, id);
                insert.setBytes(2, new byte[16]);
                insert.executeUpdate();
            }

            Punishment ban = LibertyBansReader.read(connection, NOW, Logger.getAnonymousLogger())
                    .records()
                    .getFirst()
                    .punishment();

            assertEquals(victim, ban.target());
            assertEquals(VanillaBanParser.CONSOLE_ID, ban.issuer().issuer());
        }
    }

    @Test
    void failedRowsAreLogged() throws Exception {
        List<LogRecord> logged = new ArrayList<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });

        read(logger);

        assertEquals(1, logged.size());
        assertTrue(logged.getFirst().getMessage().contains("punishment 8"));
    }

    @Test
    void unsupportedVendorIsRejected(@TempDir File folder) throws Exception {
        Files.writeString(new File(folder, "sql.yml").toPath(), "rdms-vendor: SQLITE\n");

        assertThrows(SQLException.class, () -> LibertyBansConnection.open(folder));
    }
}
