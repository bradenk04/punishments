package com.bradenkennedy.punishment.importer;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.api.model.Revocation;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AdvancedBanReader {

    private static final long PERMANENT = -1;

    private enum Kind {
        BAN(Optional.of(PunishmentType.BAN)),
        TEMP_BAN(Optional.of(PunishmentType.BAN)),
        MUTE(Optional.of(PunishmentType.MUTE)),
        TEMP_MUTE(Optional.of(PunishmentType.MUTE)),
        WARNING(Optional.of(PunishmentType.WARN)),
        TEMP_WARNING(Optional.of(PunishmentType.WARN)),
        KICK(Optional.of(PunishmentType.KICK)),
        IP_BAN(Optional.empty()),
        TEMP_IP_BAN(Optional.empty()),
        NOTE(Optional.empty());

        private final Optional<PunishmentType> imported;

        Kind(Optional<PunishmentType> imported) {
            this.imported = imported;
        }
    }

    private AdvancedBanReader() {}

    public static ImportBatch read(Connection connection, Instant now, Logger logger) throws SQLException {
        Set<String> active = activeKeys(connection);
        List<Row> rows = rows(connection);
        Map<String, UUID> operators = operators(rows);
        List<ImportRecord> records = new ArrayList<>();
        int skipped = 0;
        int failed = 0;
        for (Row row : rows) {
            try {
                Kind kind = Kind.valueOf(row.type.toUpperCase(Locale.ROOT));
                if (kind.imported.isEmpty()) {
                    skipped++;
                    continue;
                }
                records.add(new ImportRecord(
                        String.valueOf(row.id), punishment(row, kind.imported.get(), active, operators, now)));
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not read AdvancedBan history row " + row.id, e);
                failed++;
            }
        }
        return new ImportBatch(records, skipped, failed);
    }

    private record Row(
            long id, String name, String uuid, String reason, String operator, String type, long start, long end) {}

    private static List<Row> rows(Connection connection) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT * FROM PunishmentHistory ORDER BY id")) {
            while (rs.next()) {
                rows.add(new Row(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("uuid"),
                        rs.getString("reason"),
                        rs.getString("operator"),
                        rs.getString("punishmentType"),
                        rs.getLong("start"),
                        rs.getLong("end")));
            }
        }
        return rows;
    }

    private static Set<String> activeKeys(Connection connection) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT * FROM Punishments")) {
            while (rs.next()) {
                keys.add(key(rs.getString("uuid"), rs.getString("punishmentType"), rs.getLong("start")));
            }
        }
        return keys;
    }

    private static Map<String, UUID> operators(List<Row> rows) {
        Map<String, UUID> operators = new HashMap<>();
        for (Row row : rows) {
            if (row.name == null || !validUuid(row.uuid)) continue;
            operators.putIfAbsent(row.name.toLowerCase(Locale.ROOT), uuid(row.uuid));
        }
        return operators;
    }

    private static boolean validUuid(String value) {
        try {
            uuid(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Punishment punishment(
            Row row, PunishmentType type, Set<String> active, Map<String, UUID> operators, Instant now) {
        Instant start = Instant.ofEpochMilli(row.start);
        Instant expiry = type == PunishmentType.KICK || row.end == PERMANENT ? null : Instant.ofEpochMilli(row.end);
        UUID issuer = row.operator == null
                ? VanillaBanParser.CONSOLE_ID
                : operators.getOrDefault(row.operator.toLowerCase(Locale.ROOT), VanillaBanParser.CONSOLE_ID);
        boolean revoked = type != PunishmentType.KICK
                && !active.contains(key(row.uuid, row.type, row.start))
                && (expiry == null || expiry.isAfter(now));
        return new Punishment(
                UUID.randomUUID(),
                uuid(row.uuid),
                type,
                new PunishmentIssuer(issuer, start),
                row.reason,
                expiry,
                revoked ? new Revocation(VanillaBanParser.CONSOLE_ID, null, start) : null);
    }

    private static String key(String uuid, String type, long start) {
        return uuid + "|" + type + "|" + start;
    }

    private static UUID uuid(String value) {
        String dashed = value.length() == 32
                ? value.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5")
                : value;
        return UUID.fromString(dashed);
    }
}
