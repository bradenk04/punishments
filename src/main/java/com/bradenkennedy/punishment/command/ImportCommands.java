package com.bradenkennedy.punishment.command;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.migration.ImportService;
import com.bradenkennedy.punishment.migration.ImportStore;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.incendo.cloud.CommandManager;

public final class ImportCommands {
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<String, ImportService.Options> sources;
    private final String destination;
    private final Executor mainThread;
    private final PluginConfig messages;
    private final BiConsumer<CommandSender, Component> delivery;
    private final Consumer<Punishment> applied;

    public ImportCommands(
            ConfigurationSection settings,
            Path dataDirectory,
            Executor mainThread,
            PluginConfig messages,
            BiConsumer<CommandSender, Component> delivery,
            Consumer<Punishment> applied) {
        this.mainThread = mainThread;
        this.messages = messages;
        this.delivery = delivery;
        this.applied = applied;
        destination = "jdbc:h2:" + dataDirectory.resolve("punishments").toAbsolutePath();
        var options = new HashMap<String, ImportService.Options>();
        for (String source : Set.of("vanilla", "essentials", "advancedban", "litebans", "libertybans")) {
            String base = "imports." + source + ".";
            options.put(
                    source,
                    new ImportService.Options(
                            Path.of(settings.getString(base + "directory", ".")),
                            settings.getString(base + "url", ""),
                            settings.getString(base + "username", ""),
                            settings.getString(base + "password", ""),
                            settings.getString(base + "prefix", source.equals("advancedban") ? "" : source + "_")));
        }
        sources = Map.copyOf(options);
    }

    public void register(CommandManager<CommandSender> manager) {
        manager.command(manager.commandBuilder("punish")
                .literal("import")
                .required("source", stringParser())
                .flag(manager.flagBuilder("dry-run"))
                .permission("punishments.import")
                .handler(context -> {
                    CommandSender sender = context.sender();
                    if (!(sender instanceof ConsoleCommandSender)) {
                        send(sender, "console-only");
                        return;
                    }
                    String source = context.get("source");
                    if (!sources.containsKey(source)) {
                        send(sender, "unknown-source");
                        return;
                    }
                    if (!running.compareAndSet(false, true)) {
                        send(sender, "running");
                        return;
                    }
                    boolean dryRun = context.flags().isPresent("dry-run");
                    try (var store = new ImportStore(destination, dryRun)) {
                        new ImportService(
                                        store,
                                        dryRun,
                                        progress -> send(
                                                sender,
                                                dryRun ? "preview" : "progress",
                                                Placeholder.unparsed("imported", Long.toString(progress.imported())),
                                                Placeholder.unparsed("skipped", Long.toString(progress.skipped())),
                                                Placeholder.unparsed("failed", Long.toString(progress.failed()))),
                                        applied)
                                .run(source, sources.get(source));
                    } catch (Exception failure) {
                        send(sender, "failed");
                    } finally {
                        running.set(false);
                    }
                }));
    }

    private void send(CommandSender sender, String key, TagResolver... values) {
        mainThread.execute(() -> delivery.accept(sender, messages.message("imports." + key, values)));
    }
}
