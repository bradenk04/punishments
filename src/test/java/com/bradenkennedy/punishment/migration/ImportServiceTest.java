package com.bradenkennedy.punishment.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportServiceTest {
    @TempDir
    Path directory;

    private final UUID target = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final List<Punishment> applied = new ArrayList<>();
    private H2PunishmentRepository repository;
    private String destination;

    @BeforeEach
    void open() throws SQLException {
        repository = new H2PunishmentRepository(directory.toFile());
        destination = "jdbc:h2:" + directory.resolve("punishments");
    }

    @AfterEach
    void close() throws Exception {
        repository.connectionSource.close();
    }

    private ImportService.Progress run(String source, String url, boolean dry) throws Exception {
        try (var store = new ImportStore(destination, dry)) {
            return new ImportService(store, dry, progress -> {}, applied::add)
                    .run(source, new ImportService.Options(directory, url, "", "", null));
        }
    }

    private String fixture(String name) throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/imports/" + name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String database(String source) throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;NON_KEYWORDS=END,START,OPERATOR";
        try (var connection = DriverManager.getConnection(url)) {
            for (String sql : fixture(source + ".sql").split(";")) {
                if (sql.isBlank()) continue;
                try (var statement = connection.prepareStatement(sql)) {
                    statement.execute();
                }
            }
        }
        return url;
    }

    @Test
    void vanillaDryRunHasNoWritesAndRerunsAreIdempotent() throws Exception {
        Files.writeString(directory.resolve("banned-players.json"), fixture("banned-players.json"));
        Files.writeString(directory.resolve("banned-ips.json"), fixture("banned-ips.json"));
        assertEquals(new ImportService.Progress(1, 1, 1), run("vanilla", "", true));
        assertTrue(repository.findHistory(target).isEmpty());
        assertTrue(applied.isEmpty());
        try (var connection = DriverManager.getConnection(destination);
                var query = connection.prepareStatement(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='PUNISHMENT_IMPORTS'");
                var result = query.executeQuery()) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1));
        }
        assertEquals(new ImportService.Progress(1, 1, 1), run("vanilla", "", false));
        var ban = repository.findActive(target, PunishmentType.BAN).orElseThrow();
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), ban.issuer().issuedAt());
        assertEquals(List.of(ban), applied);
        assertEquals(new ImportService.Progress(0, 2, 1), run("vanilla", "", false));
        assertEquals(1, repository.findHistory(target).size());
    }

    @Test
    void essentialsImportsMutesAndVanillaFromServerRoot() throws Exception {
        Path userdata = Files.createDirectories(directory.resolve("plugins/Essentials/userdata"));
        Files.writeString(userdata.resolve(target + ".yml"), fixture("essentials.yml"));
        Files.writeString(directory.resolve("banned-players.json"), fixture("banned-players.json"));
        assertEquals(2, run("essentials", "", true).imported());
        assertTrue(repository.findHistory(target).isEmpty());
        assertEquals(2, run("essentials", "", false).imported());
        assertTrue(repository.findActive(target, PunishmentType.MUTE).isPresent());
        assertTrue(repository.findActive(target, PunishmentType.BAN).isPresent());
        assertEquals(2, applied.size());
        assertEquals(0, run("essentials", "", false).imported());
    }

    @Test
    void advancedBanKeepsActiveAndRevokedHistory() throws Exception {
        String source = database("advancedban");
        assertEquals(2, run("advancedban", source, true).imported());
        assertTrue(repository.findHistory(target).isEmpty());
        assertEquals(new ImportService.Progress(2, 1, 0), run("advancedban", source, false));
        assertTrue(repository.findActive(target, PunishmentType.BAN).isPresent());
        assertTrue(repository.findActive(target, PunishmentType.MUTE).isEmpty());
        assertEquals(1, applied.size());
        assertEquals(0, run("advancedban", source, false).imported());
    }

    @Test
    void liteBansKeepsRevocationMetadataAndDistinctTableIds() throws Exception {
        String source = database("litebans");
        assertEquals(4, run("litebans", source, true).imported());
        assertTrue(repository.findHistory(target).isEmpty());
        assertEquals(4, run("litebans", source, false).imported());
        assertTrue(repository.findActive(target, PunishmentType.BAN).isEmpty());
        Punishment ban = repository.findHistory(target).stream()
                .filter(p -> p.type() == PunishmentType.BAN)
                .findFirst()
                .orElseThrow();
        assertEquals(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                ban.issuer().issuer());
        assertEquals(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                ban.revocation().by());
        assertEquals(Instant.ofEpochMilli(1700000100000L), ban.revocation().at());
        assertEquals("Appeal", ban.revocation().reason());
        assertEquals(0, run("litebans", source, false).imported());
        assertEquals(4, repository.findHistory(target).size());
    }

    @Test
    void libertyBansReadsBinaryUuidsAndSeconds() throws Exception {
        String source = database("libertybans");
        assertEquals(1, run("libertybans", source, true).imported());
        assertTrue(repository.findHistory(target).isEmpty());
        assertEquals(1, run("libertybans", source, false).imported());
        assertEquals(
                Instant.ofEpochSecond(1700000000),
                repository
                        .findActive(target, PunishmentType.BAN)
                        .orElseThrow()
                        .issuer()
                        .issuedAt());
        assertEquals(0, run("libertybans", source, false).imported());
    }

    @Test
    void storesCurrentPunishmentModel() throws Exception {
        try (var store = new ImportStore(destination, false)) {
            var punishment = new Punishment(
                    UUID.randomUUID(),
                    target,
                    PunishmentType.BAN,
                    new com.bradenkennedy.punishment.api.model.PunishmentIssuer(target, Instant.EPOCH),
                    "test",
                    null,
                    null);
            assertTrue(store.store(punishment, "vanilla", "one", "Staff", "Player", false));
            assertEquals(
                    punishment,
                    repository.findActive(target, PunishmentType.BAN).orElseThrow());
        }
    }

    @Test
    void provenanceFailureRollsBackPunishmentAndAllowsRetry() throws Exception {
        String source = database("libertybans");
        run("libertybans", source, false);
        Punishment existing = applied.getFirst();
        Punishment record = new Punishment(
                UUID.randomUUID(),
                target,
                existing.type(),
                existing.issuer(),
                existing.reason(),
                existing.expiry(),
                null);
        try (var store = new ImportStore(destination, false)) {
            assertThrows(
                    SQLException.class,
                    () -> store.store(record, "vanilla", "record", "x".repeat(300), "Player", false));
            assertEquals(1, repository.findHistory(target).size());
            assertTrue(store.store(record, "vanilla", "record", "Staff", "Player", false));
        }
        assertEquals(2, repository.findHistory(target).size());
    }
}
