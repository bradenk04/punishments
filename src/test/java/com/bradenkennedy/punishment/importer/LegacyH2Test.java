package com.bradenkennedy.punishment.importer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import java.util.ServiceLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyH2Test {

    private static final Path JAR = Path.of(System.getProperty("legacyH2.jar"));

    @TempDir
    Path folder;

    private Path liteBans;
    private Path libFolder;

    @BeforeEach
    void createLiteBansDatabase() throws Exception {
        liteBans = Files.createDirectories(folder.resolve("LiteBans"));
        libFolder = folder.resolve("lib");
        Files.writeString(liteBans.resolve("config.yml"), "sql:\n  driver: H2\n");
        String script = new String(
                getClass().getResourceAsStream("/import/litebans/schema.sql").readAllBytes(), StandardCharsets.UTF_8);
        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {JAR.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Driver driver = ServiceLoader.load(Driver.class, loader).findFirst().orElseThrow();
            try (Connection connection = driver.connect("jdbc:h2:" + liteBans.resolve("litebans"), new Properties());
                    Statement statement = connection.createStatement()) {
                for (String sql : script.split("\n")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
                statement.execute("SHUTDOWN");
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private LegacyH2 legacyH2() {
        return new LegacyH2(libFolder, JAR.toUri(), LegacyH2.SHA256);
    }

    @Test
    void pinnedChecksumMatchesTheMavenCentralJar() throws Exception {
        assertEquals(LegacyH2.SHA256, sha256(JAR));
    }

    @Test
    void readsLegacyH2LiteBansDatabase() throws Exception {
        try (LiteBansDatabase database = LiteBansDatabase.open(liteBans.toFile(), legacyH2())) {
            ImportBatch batch = LiteBansReader.read(database, Instant.parse("2026-01-01T00:00:00Z"));

            assertEquals(6, batch.records().size());
            assertEquals(1, batch.skipped());
            assertEquals(1, batch.failed());
        }
    }

    @Test
    void leavesLiteBansFileUntouchedAndCleansUpSnapshot() throws Exception {
        Path file = liteBans.resolve("litebans.mv.db");
        byte[] before = Files.readAllBytes(file);

        try (LiteBansDatabase database = LiteBansDatabase.open(liteBans.toFile(), legacyH2())) {
            LiteBansReader.read(database, Instant.now());
        }

        assertArrayEquals(before, Files.readAllBytes(file));
        assertTrue(Files.exists(libFolder.resolve("h2-1.4.199.jar")));
    }

    @Test
    void rejectsDownloadWithWrongChecksum() {
        LegacyH2 tampered = new LegacyH2(libFolder, JAR.toUri(), "0".repeat(64));

        assertThrows(IOException.class, () -> LiteBansDatabase.open(liteBans.toFile(), tampered));
        assertFalse(Files.exists(libFolder.resolve("h2-1.4.199.jar")));
        assertEquals(0, new File(libFolder.toString()).list().length);
    }

    @Test
    void failsClearlyWhenDatabaseFileIsMissing() throws Exception {
        Files.delete(liteBans.resolve("litebans.mv.db"));

        assertThrows(IOException.class, () -> LiteBansDatabase.open(liteBans.toFile(), legacyH2()));
    }
}
