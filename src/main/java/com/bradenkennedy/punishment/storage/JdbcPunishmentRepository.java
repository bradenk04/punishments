package com.bradenkennedy.punishment.storage;

import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.storage.model.NetworkChange;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public final class JdbcPunishmentRepository implements PunishmentRepository, AutoCloseable {
    private final Connection connection;
    private final boolean mysql;
    private final boolean h2;

    public JdbcPunishmentRepository(String url, String username, String password) throws SQLException {
        mysql = url.startsWith("jdbc:mysql:");
        h2 = url.startsWith("jdbc:h2:");
        connection = DriverManager.getConnection(url, username, password);
        try {
            update("""
                    CREATE TABLE IF NOT EXISTS `punishments` (
                    `id` VARCHAR(48) PRIMARY KEY, `target` VARCHAR(48) NOT NULL,
                    `type` VARCHAR(100) NOT NULL, `issuerId` VARCHAR(48) NOT NULL,
                    `issuedAt` BIGINT NOT NULL, `reason` TEXT, `expiry` BIGINT,
                    `revoked` BOOLEAN NOT NULL, `revokedReason` TEXT, `revokedBy` VARCHAR(48),
                    `revokedAt` BIGINT, `acknowledged` BOOLEAN NOT NULL)
                    """);
            update("""
                    CREATE TABLE IF NOT EXISTS `punishment_players` (
                    `id` VARCHAR(48) PRIMARY KEY, `name` VARCHAR(255) NOT NULL)
                    """);
            update("""
                    CREATE TABLE IF NOT EXISTS `punishment_changes` (
                    `id` VARCHAR(48) PRIMARY KEY, `punishmentId` VARCHAR(48) NOT NULL,
                    `target` VARCHAR(48) NOT NULL, `action` VARCHAR(16) NOT NULL, `created` BIGINT NOT NULL)
                    """);
        } catch (RuntimeException failure) {
            try {
                connection.close();
            } catch (SQLException close) {
                failure.addSuppressed(close);
            }
            throw failure;
        }
    }

    private PreparedStatement prepare(String sql, Object... values) throws SQLException {
        String delimiter = mysql ? "`" : "\"";
        String translated = Pattern.compile("`([^`]+)`")
                .matcher(sql)
                .replaceAll(match ->
                        delimiter + (h2 ? match.group(1).toUpperCase(Locale.ROOT) : match.group(1)) + delimiter);
        var statement = connection.prepareStatement(translated);
        try {
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                statement.setObject(i + 1, value instanceof UUID ? value.toString() : value);
            }
            return statement;
        } catch (SQLException failure) {
            try {
                statement.close();
            } catch (SQLException close) {
                failure.addSuppressed(close);
            }
            throw failure;
        }
    }

    private int update(String sql, Object... values) {
        try (var statement = prepare(sql, values)) {
            return statement.executeUpdate();
        } catch (SQLException failure) {
            throw failure(failure);
        }
    }

    private List<Punishment> query(String sql, Object... values) {
        try (var statement = prepare(sql, values);
                var rows = statement.executeQuery()) {
            List<Punishment> result = new ArrayList<>();
            while (rows.next()) {
                Long expiry = (Long) rows.getObject("expiry");
                boolean revoked = rows.getBoolean("revoked");
                result.add(new Punishment(
                        UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("target")),
                        PunishmentType.valueOf(rows.getString("type")),
                        new PunishmentIssuer(
                                UUID.fromString(rows.getString("issuerId")),
                                Instant.ofEpochMilli(rows.getLong("issuedAt"))),
                        rows.getString("reason"),
                        expiry == null ? null : Instant.ofEpochMilli(expiry),
                        revoked
                                ? new Revocation(
                                        UUID.fromString(Objects.requireNonNullElse(
                                                rows.getString("revokedBy"), new UUID(0, 0).toString())),
                                        rows.getString("revokedReason"),
                                        Instant.ofEpochMilli(rows.getLong("revokedAt")))
                                : null));
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw failure(failure);
        }
    }

    @Override
    public synchronized void create(Punishment p) {
        write(p, true);
    }

    @Override
    public synchronized void revoke(UUID id, UUID issuer, String reason, Instant at) {
        findById(id)
                .ifPresent(p -> write(
                        new Punishment(
                                p.id(),
                                p.target(),
                                p.type(),
                                p.issuer(),
                                p.reason(),
                                p.expiry(),
                                new Revocation(issuer, reason, at)),
                        false));
    }

    private void write(Punishment p, boolean create) {
        try {
            connection.setAutoCommit(false);
            var revoke = p.revocation();
            if (create) {
                update(
                        """
                        INSERT INTO `punishments` (`id`,`target`,`type`,`issuerId`,`issuedAt`,`reason`,`expiry`,
                        `revoked`,`revokedBy`,`revokedAt`,`revokedReason`,`acknowledged`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                        p.id(),
                        p.target(),
                        p.type().name(),
                        p.issuer().issuer(),
                        p.issuer().issuedAt().toEpochMilli(),
                        p.reason(),
                        p.expiry() == null ? null : p.expiry().toEpochMilli(),
                        p.revoked(),
                        revoke == null ? null : revoke.by(),
                        revoke == null ? null : revoke.at().toEpochMilli(),
                        revoke == null ? null : revoke.reason(),
                        false);
            } else {
                update(
                        "UPDATE `punishments` SET `revoked`=?,`revokedBy`=?,`revokedAt`=?,`revokedReason`=? WHERE `id`=?",
                        true,
                        revoke.by(),
                        revoke.at().toEpochMilli(),
                        revoke.reason(),
                        p.id());
            }
            update(
                    "INSERT INTO `punishment_changes` (`id`,`punishmentId`,`target`,`action`,`created`) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    p.id(),
                    p.target(),
                    create ? "ISSUE" : "REVOKE",
                    Instant.now().toEpochMilli());
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollback) {
                failure.addSuppressed(rollback);
            }
            throw new IllegalStateException("Punishment database write failed", failure);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException failure) {
                throw failure(failure);
            }
        }
    }

    public synchronized void rememberName(UUID id, String name) {
        if (name == null || name.isBlank()) return;
        if (h2) update("MERGE INTO `punishment_players` (`id`,`name`) KEY (`id`) VALUES (?,?)", id, name);
        else if (mysql)
            update(
                    "INSERT INTO `punishment_players` (`id`,`name`) VALUES (?,?) ON DUPLICATE KEY UPDATE `name`=?",
                    id,
                    name,
                    name);
        else
            update(
                    "INSERT INTO `punishment_players` (`id`,`name`) VALUES (?,?) ON CONFLICT (`id`) DO UPDATE SET `name`=EXCLUDED.`name`",
                    id,
                    name);
    }

    @Override
    public synchronized Optional<Punishment> findActive(UUID player, PunishmentType type) {
        return findHistory(player).stream()
                .filter(p -> p.type() == type && !p.revoked() && !p.expired())
                .findFirst();
    }

    @Override
    public synchronized List<Punishment> findHistory(UUID player) {
        return query("SELECT * FROM `punishments` WHERE `target`=? ORDER BY `issuedAt` DESC,`id`", player);
    }

    @Override
    public synchronized HistoryPage findHistory(UUID player, Optional<PunishmentType> type, int offset, int limit) {
        if (offset < 0 || limit < 1) throw new IllegalArgumentException("Invalid history page");
        var history = findHistory(player).stream()
                .filter(p -> type.isEmpty() || p.type() == type.get())
                .toList();
        return new HistoryPage(history.stream().skip(offset).limit(limit).toList(), history.size());
    }

    public synchronized Optional<Punishment> findById(UUID id) {
        return query("SELECT * FROM `punishments` WHERE `id`=?", id).stream().findFirst();
    }

    @Override
    public synchronized List<Punishment> findUnacknowledgedWarnings(UUID player) {
        return query(
                        "SELECT * FROM `punishments` WHERE `target`=? AND `type`=? AND `acknowledged`=? AND `revoked`=?",
                        player,
                        PunishmentType.WARN.name(),
                        false,
                        false)
                .stream()
                .filter(p -> !p.expired())
                .toList();
    }

    @Override
    public synchronized void acknowledge(UUID id) {
        update("UPDATE `punishments` SET `acknowledged`=? WHERE `id`=?", true, id);
    }

    public synchronized List<NetworkChange> changesSince(long started) {
        try (var statement = prepare("SELECT * FROM `punishment_changes` WHERE `created`>=?", started);
                var rows = statement.executeQuery()) {
            List<NetworkChange> changes = new ArrayList<>();
            while (rows.next())
                changes.add(new NetworkChange(
                        UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("punishmentId")),
                        UUID.fromString(rows.getString("target")),
                        rows.getString("action"),
                        rows.getLong("created")));
            return List.copyOf(changes);
        } catch (SQLException failure) {
            throw failure(failure);
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }

    private static IllegalStateException failure(SQLException failure) {
        return new IllegalStateException("Punishment database operation failed", failure);
    }
}
