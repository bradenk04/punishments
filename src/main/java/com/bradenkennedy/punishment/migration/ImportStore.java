package com.bradenkennedy.punishment.migration;

import com.bradenkennedy.punishment.api.model.Punishment;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

public final class ImportStore implements AutoCloseable {
    private final Connection connection;

    public ImportStore(String url, boolean dryRun) throws SQLException {
        connection = DriverManager.getConnection(url);
        try {
            if (!dryRun) {
                try (var statement = connection.prepareStatement("""
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
            try (var insert = connection.prepareStatement("""
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
            try (var insert = connection.prepareStatement("""
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

    private boolean contains(UUID id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM \"PUNISHMENTS\" WHERE \"ID\" = ?")) {
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
