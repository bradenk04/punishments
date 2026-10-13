package com.bradenkennedy.punishment.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvancedBanReaderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID STAFF = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static void createSchema(Connection connection) throws Exception {
        String script = new String(
                AdvancedBanReaderTest.class
                        .getResourceAsStream("/import/advancedban/schema.sql")
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.split("\n")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
    }

    private static ImportBatch read() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "")) {
            createSchema(connection);
            return AdvancedBanReader.read(connection, NOW, Logger.getAnonymousLogger());
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

        assertEquals(7, batch.records().size());
        assertEquals(2, batch.skipped());
        assertEquals(1, batch.failed());
    }

    @Test
    void activePermanentBanKeepsDetailsAndResolvedIssuer() throws Exception {
        Punishment ban = byTarget(read(), "aaaaaaaa-bbbb");

        assertEquals(PunishmentType.BAN, ban.type());
        assertEquals("Griefing", ban.reason());
        assertEquals(Instant.ofEpochMilli(1700000000000L), ban.issuer().issuedAt());
        assertEquals(STAFF, ban.issuer().issuer());
        assertNull(ban.expiry());
        assertFalse(ban.revoked());
    }

    @Test
    void removedUnexpiredPunishmentIsRevoked() throws Exception {
        Punishment tempBan = byTarget(read(), "22222222");

        assertEquals(PunishmentType.BAN, tempBan.type());
        assertTrue(tempBan.revoked());
        assertEquals(Instant.ofEpochMilli(4102444800000L), tempBan.expiry());
    }

    @Test
    void expiredPunishmentIsNotRevoked() throws Exception {
        Punishment mute = byTarget(read(), "44444444");

        assertFalse(mute.revoked());
        assertTrue(mute.expired());
    }

    @Test
    void unresolvedOperatorFallsBackToConsole() throws Exception {
        assertEquals(
                VanillaBanParser.CONSOLE_ID,
                byTarget(read(), "33333333").issuer().issuer());
    }

    @Test
    void mapsWarningsAndKicks() throws Exception {
        ImportBatch batch = read();

        assertEquals(PunishmentType.WARN, byTarget(batch, "55555555").type());
        assertTrue(byTarget(batch, "55555555").revoked());
        assertEquals(PunishmentType.KICK, byTarget(batch, "66666666").type());
        assertFalse(byTarget(batch, "66666666").revoked());
        assertNull(byTarget(batch, "66666666").expiry());
    }

    @Test
    void sourceIdsAreHistoryRowIds() throws Exception {
        assertEquals("0", read().records().getFirst().sourceId());
    }

    @Test
    void readsDefaultHsqldbFileReadOnly(@TempDir File pluginFolder) throws Exception {
        File data = new File(pluginFolder, "data");
        assertTrue(data.mkdirs());
        try (Connection connection =
                DriverManager.getConnection("jdbc:hsqldb:file:" + new File(data, "storage").getPath(), "SA", "")) {
            createSchema(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }

        try (Connection connection = AdvancedBanConnection.open(pluginFolder)) {
            assertEquals(
                    7,
                    AdvancedBanReader.read(connection, NOW, Logger.getAnonymousLogger())
                            .records()
                            .size());
        }
        assertTrue(Arrays.stream(data.list()).anyMatch(name -> name.startsWith("storage")));
    }

    @Test
    void buildsMysqlUrlFromAdvancedBanSettings() {
        var settings = new YamlConfiguration();
        settings.set("MySQL.IP", "db.example");
        settings.set("MySQL.Port", 3307);
        settings.set("MySQL.DB-Name", "bans");
        settings.set("MySQL.Properties", "useSSL=false");

        assertEquals("jdbc:mysql://db.example:3307/bans?useSSL=false", AdvancedBanConnection.mysqlUrl(settings));
    }
}
