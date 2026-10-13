package com.bradenkennedy.punishment.command;

import static org.incendo.cloud.parser.standard.EnumParser.enumParser;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.importer.ImportBatch;
import com.bradenkennedy.punishment.importer.ImportReport;
import com.bradenkennedy.punishment.importer.ImportSource;
import com.bradenkennedy.punishment.importer.PunishmentImporter;
import com.bradenkennedy.punishment.importer.VanillaBanParser;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public final class ImportCommands {

    private final BukkitAudiences audiences;
    private final PluginConfig config;
    private final CommandSupport support;
    private final PunishmentImporter importer;
    private final File serverFolder;
    private final Logger logger;

    public ImportCommands(
            BukkitAudiences audiences,
            PluginConfig config,
            CommandSupport support,
            PunishmentImporter importer,
            File serverFolder,
            Logger logger) {
        this.audiences = audiences;
        this.config = config;
        this.support = support;
        this.importer = importer;
        this.serverFolder = serverFolder;
        this.logger = logger;
    }

    public void register(CommandManager<CommandSender> manager) {
        manager.command(manager.commandBuilder("punish")
                .literal("import")
                .required("source", enumParser(ImportSource.class))
                .flag(manager.flagBuilder("dry-run"))
                .senderType(ConsoleCommandSender.class)
                .permission("punishments.import")
                .handler(ctx -> support.execute(ctx, () -> importFrom(ctx))));
    }

    private void importFrom(CommandContext<ConsoleCommandSender> ctx) {
        Audience audience = audiences.sender(ctx.sender());
        ImportSource source = ctx.get("source");
        boolean dryRun = ctx.flags().isPresent("dry-run");
        ImportBatch batch;
        try {
            batch = read(source);
        } catch (IOException | RuntimeException e) {
            logger.log(Level.SEVERE, "Could not read " + source + " data", e);
            audience.sendMessage(config.message("import.read-failed"));
            return;
        }
        ImportReport report = importer.run(
                source,
                batch,
                dryRun,
                processed -> audience.sendMessage(config.message(
                        "import.progress",
                        Placeholder.unparsed("processed", String.valueOf(processed)),
                        Placeholder.unparsed(
                                "total", String.valueOf(batch.records().size())))));
        audience.sendMessage(config.message(
                dryRun ? "import.dry-run-report" : "import.report",
                Placeholder.unparsed("imported", String.valueOf(report.imported())),
                Placeholder.unparsed("skipped", String.valueOf(report.skipped())),
                Placeholder.unparsed("failed", String.valueOf(report.failed()))));
    }

    private ImportBatch read(ImportSource source) throws IOException {
        return switch (source) {
            case VANILLA -> readVanilla();
        };
    }

    private ImportBatch readVanilla() throws IOException {
        ImportBatch players;
        try (Reader reader = open("banned-players.json")) {
            players = VanillaBanParser.parsePlayers(reader);
        }
        File ips = new File(serverFolder, "banned-ips.json");
        if (!ips.exists()) return players;
        try (Reader reader = open("banned-ips.json")) {
            return players.plus(VanillaBanParser.parseIps(reader));
        }
    }

    private Reader open(String fileName) throws IOException {
        return Files.newBufferedReader(new File(serverFolder, fileName).toPath(), StandardCharsets.UTF_8);
    }
}
