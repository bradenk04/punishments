package com.bradenkennedy.punishment.importer;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.api.model.Revocation;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public final class LiteBansReader {

    private enum Table {
        BANS("bans", PunishmentType.BAN),
        MUTES("mutes", PunishmentType.MUTE),
        WARNINGS("warnings", PunishmentType.WARN),
        KICKS("kicks", PunishmentType.KICK);

        private final String suffix;
        private final PunishmentType type;

        Table(String suffix, PunishmentType type) {
            this.suffix = suffix;
            this.type = type;
        }
    }

    private LiteBansReader() {}

    public static ImportBatch read(LiteBansDatabase database, Instant now) throws SQLException {
        ImportBatch batch = new ImportBatch(List.of(), 0, 0);
        for (Table table : Table.values()) {
            batch = batch.plus(read(database.connection(), database.tablePrefix(), table, now));
        }
        return batch;
    }

    private static ImportBatch read(Connection connection, String prefix, Table table, Instant now)
            throws SQLException {
        List<ImportRecord> records = new ArrayList<>();
        int skipped = 0;
        int failed = 0;
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT * FROM " + prefix + table.suffix + " ORDER BY id")) {
            while (rs.next()) {
                if (rs.getBoolean("ipban")) {
                    skipped++;
                    continue;
                }
                try {
                    records.add(new ImportRecord(table.suffix + ":" + rs.getLong("id"), punishment(rs, table, now)));
                } catch (RuntimeException e) {
                    failed++;
                }
            }
        }
        return new ImportBatch(records, skipped, failed);
    }

    private static Punishment punishment(ResultSet rs, Table table, Instant now) throws SQLException {
        Instant issuedAt = Instant.ofEpochMilli(rs.getLong("time"));
        long until = rs.getLong("until");
        Instant expiry = table == Table.KICKS || until <= 0 ? null : Instant.ofEpochMilli(until);
        return new Punishment(
                UUID.randomUUID(),
                UUID.fromString(rs.getString("uuid")),
                table.type,
                new PunishmentIssuer(person(rs.getString("banned_by_uuid")), issuedAt),
                rs.getString("reason"),
                expiry,
                table == Table.KICKS ? null : revocation(rs, issuedAt, expiry, now));
    }

    private static Revocation revocation(ResultSet rs, Instant issuedAt, Instant expiry, Instant now)
            throws SQLException {
        String removedByUuid = rs.getString("removed_by_uuid");
        boolean removed = removedByUuid != null || rs.getString("removed_by_name") != null;
        boolean unexpiredInactive = !rs.getBoolean("active") && (expiry == null || expiry.isAfter(now));
        if (!removed && !unexpiredInactive) return null;
        Timestamp removedAt = rs.getTimestamp("removed_by_date", Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return new Revocation(
                person(removedByUuid),
                rs.getString("removed_by_reason"),
                removedAt == null ? issuedAt : removedAt.toInstant());
    }

    private static UUID person(String value) {
        if (value == null || value.toUpperCase(Locale.ROOT).contains("CONSOLE")) {
            return VanillaBanParser.CONSOLE_ID;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return VanillaBanParser.CONSOLE_ID;
        }
    }
}
