package com.lotusplugins.warrant.command;

import static org.incendo.cloud.parser.standard.EnumParser.enumParser;

import com.lotusplugins.warrant.PluginConfig;
import com.lotusplugins.warrant.importer.ImportReport;
import com.lotusplugins.warrant.importer.ImportSource;
import com.lotusplugins.warrant.importer.ImportSources;
import com.lotusplugins.warrant.importer.PunishmentImporter;
import com.lotusplugins.warrant.importer.SourceBatch;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public final class ImportCommands {

    private final BukkitAudiences audiences;
    private final PluginConfig config;
    private final CommandSupport support;
    private final PunishmentImporter importer;
    private final ImportSources sources;
    private final Logger logger;

    public ImportCommands(
            BukkitAudiences audiences,
            PluginConfig config,
            CommandSupport support,
            PunishmentImporter importer,
            ImportSources sources,
            Logger logger) {
        this.audiences = audiences;
        this.config = config;
        this.support = support;
        this.importer = importer;
        this.sources = sources;
        this.logger = logger;
    }

    public void register(CommandManager<CommandSender> manager) {
        manager.command(manager.commandBuilder("punish")
                .literal("import")
                .required("source", enumParser(ImportSource.class))
                .flag(manager.flagBuilder("dry-run"))
                .permission("warrant.import")
                .handler(ctx -> support.execute(ctx, () -> importFrom(ctx))));
    }

    private void importFrom(CommandContext<CommandSender> ctx) {
        Audience audience = audiences.sender(ctx.sender());
        if (ctx.sender() instanceof Player) {
            audience.sendMessage(config.message("import.console-only"));
            return;
        }
        ImportSource source = ctx.get("source");
        boolean dryRun = ctx.flags().isPresent("dry-run");
        List<SourceBatch> batches;
        try {
            batches = sources.read(source);
        } catch (IOException | SQLException | RuntimeException e) {
            logger.log(Level.SEVERE, "Could not read " + source + " data", e);
            audience.sendMessage(config.message("import.read-failed"));
            return;
        }
        ImportReport report = new ImportReport(0, 0, 0);
        for (SourceBatch batch : batches) {
            report = report.plus(importer.run(
                    batch.source(),
                    batch.batch(),
                    dryRun,
                    processed -> audience.sendMessage(config.message(
                            "import.progress",
                            Placeholder.unparsed("processed", String.valueOf(processed)),
                            Placeholder.unparsed(
                                    "total",
                                    String.valueOf(batch.batch().records().size()))))));
        }
        audience.sendMessage(config.message(
                dryRun ? "import.dry-run-report" : "import.report",
                Placeholder.unparsed("imported", String.valueOf(report.imported())),
                Placeholder.unparsed("skipped", String.valueOf(report.skipped())),
                Placeholder.unparsed("failed", String.valueOf(report.failed()))));
    }
}
