package com.bradenkennedy.punishment.migration;

import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.storage.*;
import com.bradenkennedy.punishment.storage.model.*;
import com.google.gson.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Offline migration: use copies of source files and a SELECT-only source database account. */
public final class ImportService {
    public record Options(Path directory, String url, String username, String password, String prefix) {}
    public record Progress(long imported, long skipped, long failed) {}
    private final JdbcPunishmentRepository destination;
    private long imported, skipped, failed;
    private final Consumer<Progress> progress;
    private final boolean dryRun;
    private final Set<UUID> seen = new HashSet<>();
    public ImportService(JdbcPunishmentRepository destination, boolean dryRun, Consumer<Progress> progress) {
        this.destination = destination; this.dryRun = dryRun; this.progress = progress;
    }
    public Progress run(String source, Options options) throws Exception {
        switch (source.toLowerCase(Locale.ROOT)) {
            case "vanilla" -> vanilla(options.directory());
            case "essentials" -> { vanilla(options.directory()); essentials(options.directory().resolve("userdata")); }
            case "advancedban", "litebans", "libertybans" -> jdbc(source.toLowerCase(Locale.ROOT), options);
            default -> throw new IllegalArgumentException("Source must be vanilla, essentials, advancedban, litebans or libertybans");
        }
        var result = snapshot(); progress.accept(result); return result;
    }
    private Progress snapshot() { return new Progress(imported, skipped, failed); }
    private void row(Runnable action) {
        try { action.run(); } catch (RuntimeException error) { failed++; }
        if ((imported + skipped + failed) % 100 == 0) progress.accept(snapshot());
    }
    private void store(String source, String originalId, UUID target, PunishmentType type, UUID issuer,
                       Instant issued, Instant expiry, String reason, boolean revoked,
                       UUID revokedBy, Instant revokedAt, String revokedReason, String issuerName, String targetName) {
        UUID id = UUID.nameUUIDFromBytes(("punishments-import:" + source + ":" + originalId).getBytes(StandardCharsets.UTF_8));
        if (!seen.add(id)) { skipped++; return; }
        var model = new PunishmentModel(new Punishment(id, target, type,
                new PunishmentIssuer(issuer, issued), reason, expiry, revoked));
        model.setRevokedBy(revokedBy); model.setRevokedAt(revokedAt); model.setRevokedReason(revokedReason);
        var metadata = new ImportMetadata(); metadata.id = id; metadata.source = source;
        metadata.originalId = originalId; metadata.issuerName = issuerName; metadata.targetName = targetName;
        if (destination.importRecord(model, metadata, dryRun)) imported++; else skipped++;
    }
    private void vanilla(Path directory) throws Exception {
        Path players = directory.resolve("banned-players.json");
        if (Files.exists(players)) try (var reader = Files.newBufferedReader(players)) {
            for (var item : JsonParser.parseReader(reader).getAsJsonArray()) row(() -> {
                var object = item.getAsJsonObject(); UUID target = uuid(json(object, "uuid"));
                String created = json(object, "created");
                store("vanilla", target + ":" + created, target, PunishmentType.BAN, new UUID(0,0),
                        vanillaDate(created), vanillaDate(json(object,"expires")), json(object,"reason"), false,
                        null, null, null, json(object,"source"), json(object,"name"));
            });
        }
        Path ips = directory.resolve("banned-ips.json");
        if (Files.exists(ips)) try (var reader = Files.newBufferedReader(ips)) {
            skipped += JsonParser.parseReader(reader).getAsJsonArray().size();
        }
    }
    private void essentials(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) throw new IllegalArgumentException("Missing Essentials userdata directory");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yml")).toList()) row(() -> {
                try (var reader = Files.newBufferedReader(file)) {
                    Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
                    if (!(document instanceof Map<?,?> map) || !Boolean.TRUE.equals(map.get("muted"))) { skipped++; return; }
                    UUID target = uuid(file.getFileName().toString().replace(".yml", ""));
                    Object timestamps = map.get("timestamps");
                    long until = timestamps instanceof Map<?,?> times ? number(times.get("mute")) : 0;
                    store("essentials", target + ":mute:" + until, target, PunishmentType.MUTE, new UUID(0,0),
                            Instant.EPOCH, millis(until), null, false, null, null, null, null,
                            Objects.toString(map.get("last-account-name"), null));
                } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            });
        }
    }
    private void jdbc(String source, Options options) throws Exception {
        String prefix = Objects.requireNonNullElse(options.prefix(), source.equals("advancedban") ? "" : source + "_");
        if (!prefix.matches("[A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid table prefix");
        try (var connection = DriverManager.getConnection(options.url(), options.username(), options.password())) {
            connection.setReadOnly(true);
            if (source.equals("litebans")) {
                for (String table : List.of("bans", "mutes", "warnings", "kicks")) {
                    var type = switch (table) { case "bans" -> PunishmentType.BAN; case "mutes" -> PunishmentType.MUTE;
                        case "warnings" -> PunishmentType.WARN; default -> PunishmentType.KICK; };
                    select(connection, "SELECT * FROM " + prefix + table, values -> {
                        if (truth(values.get("ipban"))) { skipped++; return; }
                        Instant expiry = millis(number(values.get("until")));
                        boolean revoked = !truth(values.get("active")) && type != PunishmentType.KICK
                                && (expiry == null || expiry.isAfter(Instant.now()));
                        store(source, table + ":" + values.get("id"), uuid(values.get("uuid")), type,
                                nullableUuid(values.get("banned_by_uuid")), millis(number(values.get("time"))), expiry,
                                str(values,"reason"), revoked, optionalUuid(values.get("removed_by_uuid")),
                                millis(number(values.get("removed_by_date"))), str(values,"removed_by_reason"),
                                str(values,"banned_by_name"), null);
                    });
                }
            } else if (source.equals("advancedban")) {
                Set<String> active = new HashSet<>();
                select(connection, "SELECT * FROM " + prefix + "Punishments", values -> active.add(advancedKey(values)));
                select(connection, "SELECT * FROM " + prefix + "PunishmentHistory", values -> {
                    String kind = str(values,"punishmenttype").toUpperCase(Locale.ROOT);
                    if (kind.contains("IP")) { skipped++; return; }
                    var type = PunishmentType.valueOf(kind.replace("TEMP", ""));
                    Instant expiry = millis(number(values.get("end")));
                    boolean revoked = !active.contains(advancedKey(values)) && type != PunishmentType.KICK
                            && (expiry == null || expiry.isAfter(Instant.now()));
                    store(source, Objects.toString(values.get("id")), uuid(values.get("uuid")), type, new UUID(0,0),
                            millis(number(values.get("start"))), expiry, str(values,"reason"), revoked,
                            null, null, null, str(values,"operator"), str(values,"name"));
                });
            } else {
                Set<String> active = new HashSet<>();
                for (String table : List.of("bans","mutes","warns"))
                    select(connection, "SELECT id FROM " + prefix + table, values -> active.add(Objects.toString(values.get("id"))));
                select(connection, "SELECT p.*, v.type AS victim_type, v.data AS victim_data FROM " + prefix
                        + "punishments p JOIN " + prefix + "history h ON p.id=h.id JOIN " + prefix
                        + "victims v ON h.victim=v.id", values -> {
                    if (number(values.get("victim_type")) != 0) { skipped++; return; }
                    var type = switch ((int)number(values.get("type"))) { case 0 -> PunishmentType.BAN; case 1 -> PunishmentType.MUTE;
                        case 2 -> PunishmentType.WARN; case 3 -> PunishmentType.KICK; default -> throw new IllegalArgumentException("Unknown type"); };
                    Instant expiry = seconds(number(values.get("end")));
                    boolean revoked = !active.contains(Objects.toString(values.get("id"))) && type != PunishmentType.KICK
                            && (expiry == null || expiry.isAfter(Instant.now()));
                    store(source, Objects.toString(values.get("id")), uuid(values.get("victim_data")), type,
                            nullableUuid(values.get("operator")), seconds(number(values.get("start"))), expiry,
                            str(values,"reason"), revoked, null, null, null, null, null);
                });
            }
        }
    }
    private void select(Connection connection, String sql, Consumer<Map<String,Object>> handler) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            var metadata = result.getMetaData();
            while (result.next()) {
                Map<String,Object> values = new HashMap<>();
                for (int index=1; index<=metadata.getColumnCount(); index++)
                    values.put(metadata.getColumnLabel(index).toLowerCase(Locale.ROOT), result.getObject(index));
                row(() -> handler.accept(values));
            }
        }
    }
    private static String advancedKey(Map<String,Object> values) {
        return values.get("uuid") + ":" + values.get("start") + ":" + values.get("punishmenttype");
    }
    private static String json(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }
    private static Instant vanillaDate(String value) {
        if (value == null || value.equalsIgnoreCase("forever")) return null;
        return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")).toInstant();
    }
    private static String str(Map<String,Object> values, String key) { return Objects.toString(values.get(key), null); }
    private static long number(Object value) { return value == null ? 0 : value instanceof Number n ? n.longValue() : Long.parseLong(value.toString()); }
    private static boolean truth(Object value) { return value instanceof Boolean b ? b : value != null && number(value) != 0; }
    private static Instant millis(long value) { return value <= 0 ? null : Instant.ofEpochMilli(value); }
    private static Instant seconds(long value) { return value <= 0 ? null : Instant.ofEpochSecond(value); }
    private static UUID optionalUuid(Object value) { return value == null || value.toString().isBlank() ? null : nullableUuid(value); }
    private static UUID nullableUuid(Object value) {
        try { return uuid(value); } catch (RuntimeException ignored) { return new UUID(0,0); }
    }
    private static UUID uuid(Object value) {
        if (value instanceof byte[] bytes) {
            if (bytes.length != 16) throw new IllegalArgumentException("Not a UUID");
            var buffer = ByteBuffer.wrap(bytes); return new UUID(buffer.getLong(), buffer.getLong());
        }
        String text = Objects.requireNonNull(value).toString();
        if (text.matches("[0-9a-fA-F]{32}")) text = text.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        return UUID.fromString(text);
    }
}
