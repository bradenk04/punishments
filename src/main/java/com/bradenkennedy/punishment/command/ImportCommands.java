package com.bradenkennedy.punishment.command;
import com.bradenkennedy.punishment.PunishmentPlugin;
import com.bradenkennedy.punishment.migration.ImportService;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.incendo.cloud.CommandManager;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;
public final class ImportCommands {
    private static final AtomicBoolean running = new AtomicBoolean();
    public static void register(CommandManager<CommandSender> manager) {
        manager.command(manager.commandBuilder("punish").literal("import")
            .required("source", stringParser()).flag(manager.flagBuilder("dry-run"))
            .permission("punishments.import").handler(ctx -> {
                if (!(ctx.sender() instanceof ConsoleCommandSender)) {
                    ctx.sender().sendMessage("Imports may only be run from the server console."); return;
                }
                if (!running.compareAndSet(false, true)) {
                    ctx.sender().sendMessage("An import is already running."); return;
                }
                try {
                    String source = ctx.get("source");
                    var settings = PunishmentPlugin.getInstance().getConfig();
                    String base = "imports." + source + ".";
                    var options = new ImportService.Options(Path.of(settings.getString(base + "directory", ".")),
                        settings.getString(base + "url"), settings.getString(base + "username", ""),
                        settings.getString(base + "password", ""), settings.getString(base + "prefix"));
                    var importer = new ImportService((JdbcPunishmentRepository)PunishmentPlugin.getDataRepository(),
                        ctx.flags().isPresent("dry-run"), p -> ctx.sender().sendMessage("Import "
                        + p.imported() + " imported, " + p.skipped() + " skipped, " + p.failed() + " failed"));
                    importer.run(source, options);
                } catch (Exception failure) {
                    // Database diagnostics can contain credentials, so keep them out of command output.
                    ctx.sender().sendMessage("Import stopped. Check source configuration and database access; committed rows can be rerun safely.");
                } finally { running.set(false); }
            }));
    }
}
