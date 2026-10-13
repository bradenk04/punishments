package com.bradenkennedy.punishment.importer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class ImportSources {

    private final File serverFolder;
    private final File pluginsFolder;
    private final LegacyH2 legacyH2;
    private final Supplier<Map<String, UUID>> knownPlayers;
    private final Logger logger;

    public ImportSources(
            File serverFolder,
            File pluginsFolder,
            LegacyH2 legacyH2,
            Supplier<Map<String, UUID>> knownPlayers,
            Logger logger) {
        this.serverFolder = serverFolder;
        this.pluginsFolder = pluginsFolder;
        this.legacyH2 = legacyH2;
        this.knownPlayers = knownPlayers;
        this.logger = logger;
    }

    public List<SourceBatch> read(ImportSource source) throws IOException, SQLException {
        return switch (source) {
            case VANILLA -> List.of(new SourceBatch(ImportSource.VANILLA, vanilla()));
            case ADVANCEDBAN -> List.of(new SourceBatch(ImportSource.ADVANCEDBAN, advancedBan()));
            case LITEBANS -> List.of(new SourceBatch(ImportSource.LITEBANS, liteBans()));
            case LIBERTYBANS -> List.of(new SourceBatch(ImportSource.LIBERTYBANS, libertyBans()));
            case ESSENTIALS ->
                List.of(new SourceBatch(ImportSource.VANILLA, vanilla()), new SourceBatch(source, essentials()));
        };
    }

    private ImportBatch vanilla() throws IOException {
        ImportBatch players;
        try (Reader reader = open("banned-players.json")) {
            players = VanillaBanParser.parsePlayers(reader, logger);
        }
        if (!new File(serverFolder, "banned-ips.json").exists()) return players;
        try (Reader reader = open("banned-ips.json")) {
            return players.plus(VanillaBanParser.parseIps(reader));
        }
    }

    private ImportBatch advancedBan() throws SQLException {
        try (Connection connection = AdvancedBanConnection.open(new File(pluginsFolder, "AdvancedBan"))) {
            return AdvancedBanReader.read(connection, Instant.now(), knownPlayers.get(), logger);
        }
    }

    private ImportBatch liteBans() throws IOException, SQLException {
        try (LiteBansDatabase database = LiteBansDatabase.open(new File(pluginsFolder, "LiteBans"), legacyH2)) {
            return LiteBansReader.read(database, Instant.now(), logger);
        }
    }

    private ImportBatch libertyBans() throws SQLException {
        try (Connection connection = LibertyBansConnection.open(new File(pluginsFolder, "LibertyBans"))) {
            return LibertyBansReader.read(connection, Instant.now(), logger);
        }
    }

    private ImportBatch essentials() throws IOException {
        return EssentialsMuteReader.read(new File(pluginsFolder, "Essentials/userdata"), Instant.now(), logger);
    }

    private Reader open(String fileName) throws IOException {
        return Files.newBufferedReader(new File(serverFolder, fileName).toPath(), StandardCharsets.UTF_8);
    }
}
