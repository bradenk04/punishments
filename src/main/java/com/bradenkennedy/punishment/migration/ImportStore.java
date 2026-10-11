package com.bradenkennedy.punishment.migration;

import com.bradenkennedy.punishment.api.model.Punishment;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

public final class ImportStore implements AutoCloseable {
    private final Connection connection;
    private final boolean h2;
    private final boolean mysql;

    public ImportStore(String url, boolean dryRun) throws SQLException {
        this(url, "", "", dryRun);
    }

    public ImportStore(String url, String username, String password, boolean dryRun) throws SQLException {
        h2 = url.startsWith("jdbc:h2:");
        mysql = url.startsWith("jdbc:mysql:");
        connection = DriverManager.getConnection(url, username, password);
        try {
            if (!dryRun) {
                try (var statement = prepare("""
                        CREATE TABLE IF NOT EXISTS punishment_imports (
                          id VARCHAR(36) PRIMARY KEY, source VARCHAR(32) NOT NULL,
                          original_id VARCHAR(255) NOT NULL, issuer_name VARCHAR(255), target_name VARCHAR(255),
                          UNIQUE(source, original_id))
                        """)) {
                    statement.executeUpdate();
                }
            }
            connection.setAutoCommit(false);
        } catch (SQLException failure) {
            try {
                connection.close();
            } catch (SQLException close) {
                failure.addSuppressed(close);
            }
            throw failure;
        }
    }

    public boolean store(
            Punishment punishment,
            String source,
            String originalId,
            String issuerName,
            String targetName,
            boolean dryRun)
            throws SQLException {
        try {
            if (contains(punishment.id())) return false;
            if (dryRun) return true;
            try (var insert = prepare("""
                    INSERT INTO "PUNISHMENTS" ("ID", "TARGET", "TYPE", "ISSUERID", "ISSUEDAT", "REASON",
                      "EXPIRY", "REVOKED", "REVOKEDBY", "REVOKEDAT", "REVOKEDREASON", "ACKNOWLEDGED")
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, punishment.id().toString());
                insert.setString(2, punishment.target().toString());
                insert.setString(3, punishment.type().name());
                insert.setString(4, punishment.issuer().issuer().toString());
                insert.setLong(5, punishment.issuer().issuedAt().toEpochMilli());
                insert.setString(6, punishment.reason());
                insert.setObject(
                        7,
                        punishment.expiry() == null ? null : punishment.expiry().toEpochMilli());
                insert.setBoolean(8, punishment.revoked());
                var revocation = punishment.revocation();
                insert.setString(9, revocation == null ? null : revocation.by().toString());
                insert.setObject(10, revocation == null ? null : revocation.at().toEpochMilli());
                insert.setString(11, revocation == null ? null : revocation.reason());
                insert.setBoolean(12, false);
                insert.executeUpdate();
            }
            try (var insert = prepare("""
                    INSERT INTO punishment_imports (id, source, original_id, issuer_name, target_name)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, punishment.id().toString());
                insert.setString(2, source);
                insert.setString(3, originalId);
                insert.setString(4, issuerName);
                insert.setString(5, targetName);
                insert.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollback) {
                failure.addSuppressed(rollback);
            }
            throw failure;
        }
    }

    private java.sql.PreparedStatement prepare(String sql) throws SQLException {
        String delimiter = mysql ? "`" : "\"";
        String translated = java.util.regex.Pattern.compile("\"([^\"]+)\"")
                .matcher(sql)
                .replaceAll(match -> {
                    String name =
                            switch (match.group(1)) {
                                case "ISSUERID" -> "issuerId";
                                case "ISSUEDAT" -> "issuedAt";
                                case "REVOKEDBY" -> "revokedBy";
                                case "REVOKEDAT" -> "revokedAt";
                                case "REVOKEDREASON" -> "revokedReason";
                                default -> match.group(1).toLowerCase(java.util.Locale.ROOT);
                            };
                    return delimiter + (h2 ? match.group(1) : name) + delimiter;
                });
        return connection.prepareStatement(translated);
    }

    private boolean contains(UUID id) throws SQLException {
        try (var query = prepare("SELECT 1 FROM \"PUNISHMENTS\" WHERE \"ID\" = ?")) {
            query.setString(1, id.toString());
            try (var result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
