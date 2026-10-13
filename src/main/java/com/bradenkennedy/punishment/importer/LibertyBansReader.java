package com.bradenkennedy.punishment.importer;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.api.model.Revocation;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LibertyBansReader {

    private static final String TABLE_PREFIX = "libertybans_";
    private static final UUID WILDCARD = new UUID(0, 0);
    private static final int PLAYER_VICTIM = 0;
    private static final int ADDRESS_VICTIM = 1;
    private static final int COMPOSITE_VICTIM = 2;

    private LibertyBansReader() {}

    public static ImportBatch read(Connection connection, Instant now, Logger logger) throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString().trim();
        Set<Long> active = activeIds(connection, quote);
        List<ImportRecord> records = new ArrayList<>();
        int skipped = 0;
        int failed = 0;
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT * FROM " + quote + TABLE_PREFIX + "simple_history" + quote
                        + " ORDER BY " + quote + "id" + quote)) {
            while (rs.next()) {
                long id = rs.getLong("id");
                try {
                    int victimType = rs.getInt("victim_type");
                    if (victimType == ADDRESS_VICTIM
                            || victimType == COMPOSITE_VICTIM && WILDCARD.equals(uuid(rs.getObject("victim_uuid")))) {
                        skipped++;
                        continue;
                    }
                    if (victimType != PLAYER_VICTIM && victimType != COMPOSITE_VICTIM) {
                        throw new IllegalArgumentException("Unknown victim type " + victimType);
                    }
                    records.add(new ImportRecord(String.valueOf(id), punishment(rs, active.contains(id), now)));
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Could not read LibertyBans punishment " + id, e);
                    failed++;
                }
            }
        }
        return new ImportBatch(records, skipped, failed);
    }

    private static Set<Long> activeIds(Connection connection, String quote) throws SQLException {
        Set<Long> ids = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT " + quote + "id" + quote + " FROM " + quote + TABLE_PREFIX + "simple_active" + quote)) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static Punishment punishment(ResultSet rs, boolean active, Instant now) throws SQLException {
        PunishmentType type = type(rs.getInt("type"));
        Instant start = Instant.ofEpochSecond(rs.getLong("start"));
        long end = rs.getLong("end");
        Instant expiry = type == PunishmentType.KICK || end == 0 ? null : Instant.ofEpochSecond(end);
        boolean revoked = type != PunishmentType.KICK && !active && (expiry == null || expiry.isAfter(now));
        return new Punishment(
                UUID.randomUUID(),
                uuid(rs.getObject("victim_uuid")),
                type,
                new PunishmentIssuer(uuid(rs.getObject("operator")), start),
                rs.getString("reason"),
                expiry,
                revoked ? new Revocation(VanillaBanParser.CONSOLE_ID, null, start) : null);
    }

    private static PunishmentType type(int ordinal) {
        return switch (ordinal) {
            case 0 -> PunishmentType.BAN;
            case 1 -> PunishmentType.MUTE;
            case 2 -> PunishmentType.WARN;
            case 3 -> PunishmentType.KICK;
            default -> throw new IllegalArgumentException("Unknown punishment type " + ordinal);
        };
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        throw new IllegalArgumentException("Unsupported UUID value " + value);
    }
}
