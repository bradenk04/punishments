package com.lotusplugins.warrant.importer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Properties;
import java.util.ServiceLoader;

public final class LegacyH2 {

    public static final URI MAVEN_CENTRAL =
            URI.create("https://repo1.maven.org/maven2/com/h2database/h2/1.4.199/h2-1.4.199.jar");
    public static final String SHA256 = "3125a16743bc6b4cfbb61abba783203f1fb68230aa0fdc97898f796f99a5d42e";

    private static final String JAR_NAME = "h2-1.4.199.jar";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    public record Opened(Connection connection, Closeable cleanup) {}

    private final Path libFolder;
    private final URI source;
    private final String sha256;

    public LegacyH2(Path libFolder, URI source, String sha256) {
        this.libFolder = libFolder;
        this.source = source;
        this.sha256 = sha256;
    }

    public Opened open(Path databaseFile, String user, String password) throws IOException, SQLException {
        Path jar = ensureJar();
        Path snapshotFolder = Files.createTempDirectory("litebans-import");
        URLClassLoader loader = null;
        try {
            Files.copy(databaseFile, snapshotFolder.resolve(databaseFile.getFileName()));
            loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
            Driver driver = ServiceLoader.load(Driver.class, loader)
                    .findFirst()
                    .orElseThrow(() -> new SQLException(JAR_NAME + " contains no JDBC driver"));
            Properties credentials = new Properties();
            credentials.setProperty("user", user);
            credentials.setProperty("password", password);
            Connection connection = driver.connect(url(snapshotFolder, databaseFile), credentials);
            if (connection == null) throw new SQLException("H2 driver rejected the LiteBans data URL");
            URLClassLoader opened = loader;
            return new Opened(connection, () -> {
                opened.close();
                deleteRecursively(snapshotFolder);
            });
        } catch (IOException | SQLException | RuntimeException e) {
            if (loader != null) loader.close();
            deleteRecursively(snapshotFolder);
            throw e;
        }
    }

    private static String url(Path snapshotFolder, Path databaseFile) {
        String fileName = databaseFile.getFileName().toString();
        return "jdbc:h2:" + snapshotFolder.resolve(fileName.substring(0, fileName.indexOf('.')));
    }

    private Path ensureJar() throws IOException {
        Path jar = libFolder.resolve(JAR_NAME);
        if (Files.exists(jar) && sha256Of(jar).equals(sha256)) return jar;
        Files.createDirectories(libFolder);
        Path download = Files.createTempFile(libFolder, JAR_NAME, ".part");
        try {
            URLConnection connection = source.toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, download, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!sha256Of(download).equals(sha256)) {
                throw new IOException("Downloaded " + JAR_NAME + " does not match the expected SHA-256");
            }
            return Files.move(download, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(download);
        }
    }

    private static String sha256Of(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteRecursively(Path folder) throws IOException {
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
