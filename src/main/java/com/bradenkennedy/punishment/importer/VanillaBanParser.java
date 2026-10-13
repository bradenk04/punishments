package com.bradenkennedy.punishment.importer;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VanillaBanParser {

    public static final UUID CONSOLE_ID = new UUID(0, 0);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    private static final String FOREVER = "forever";

    private VanillaBanParser() {}

    public static ImportBatch parsePlayers(Reader reader, Logger logger) {
        List<ImportRecord> records = new ArrayList<>();
        int failed = 0;
        for (JsonElement element : entries(reader)) {
            try {
                records.add(playerBan(element.getAsJsonObject()));
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not read vanilla ban entry " + element, e);
                failed++;
            }
        }
        return new ImportBatch(records, 0, failed);
    }

    public static ImportBatch parseIps(Reader reader) {
        return new ImportBatch(List.of(), entries(reader).size(), 0);
    }

    private static JsonArray entries(Reader reader) {
        return JsonParser.parseReader(reader).getAsJsonArray();
    }

    private static ImportRecord playerBan(JsonObject entry) {
        UUID target = UUID.fromString(entry.get("uuid").getAsString());
        String created = entry.get("created").getAsString();
        String expires = entry.get("expires").getAsString();
        Instant expiry = FOREVER.equals(expires) ? null : timestamp(expires);
        Punishment ban = new Punishment(
                UUID.randomUUID(),
                target,
                PunishmentType.BAN,
                new PunishmentIssuer(CONSOLE_ID, timestamp(created)),
                entry.get("reason").getAsString(),
                expiry,
                null);
        return new ImportRecord(target + "@" + created, ban);
    }

    private static Instant timestamp(String value) {
        return OffsetDateTime.parse(value, TIMESTAMP).toInstant();
    }
}
