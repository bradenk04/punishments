package com.bradenkennedy.punishment.importer;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class EssentialsMuteReader {

    private static final String EXTENSION = ".yml";

    private EssentialsMuteReader() {}

    public static ImportBatch read(File userdataFolder, Instant now, Logger logger) throws IOException {
        File[] files = userdataFolder.listFiles((folder, name) -> name.endsWith(EXTENSION));
        if (files == null) throw new IOException("Cannot list " + userdataFolder);
        List<ImportRecord> records = new ArrayList<>();
        int skipped = 0;
        int failed = 0;
        for (File file :
                Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            try {
                YamlConfiguration userdata = new YamlConfiguration();
                userdata.load(file);
                if (userdata.getBoolean("jailed")) skipped++;
                if (!userdata.getBoolean("muted")) continue;
                records.add(mute(file, userdata, now));
            } catch (IOException | InvalidConfigurationException | RuntimeException e) {
                logger.log(Level.WARNING, "Could not read Essentials userdata " + file.getName(), e);
                failed++;
            }
        }
        return new ImportBatch(records, skipped, failed);
    }

    private static ImportRecord mute(File file, YamlConfiguration userdata, Instant now) {
        String name = file.getName();
        UUID target = UUID.fromString(name.substring(0, name.length() - EXTENSION.length()));
        long timeout = userdata.getLong("timestamps.mute", 0);
        Instant expiry = timeout <= 0 ? null : Instant.ofEpochMilli(timeout);
        Punishment mute = new Punishment(
                UUID.randomUUID(),
                target,
                PunishmentType.MUTE,
                new PunishmentIssuer(VanillaBanParser.CONSOLE_ID, now),
                userdata.getString("mute-reason"),
                expiry,
                null);
        return new ImportRecord(target + "@" + timeout, mute);
    }
}
