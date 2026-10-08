package com.bradenkennedy.punishment.command;

import static com.bradenkennedy.punishment.command.CommandSupport.nameOf;
import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;

public final class PunishmentCommands {

    private final BukkitAudiences audiences;
    private final PunishmentRepository repository;
    private final PluginConfig config;

    public PunishmentCommands(BukkitAudiences audiences, PunishmentRepository repository, PluginConfig config) {
        this.audiences = audiences;
        this.repository = repository;
        this.config = config;
    }

    public void register(CommandManager<CommandSender> manager) {
        manager.command(manager.commandBuilder("punish")
                .literal("history")
                .required("player", offlinePlayerParser())
                .permission("punishments.history")
                .handler(ctx -> {
                    OfflinePlayer target = ctx.get("player");
                    Audience audience = audiences.sender(ctx.sender());
                    var history = repository.findHistory(target.getUniqueId());
                    if (history.isEmpty()) {
                        audience.sendMessage(
                                config.message("history.empty", Placeholder.unparsed("name", nameOf(target))));
                    }
                    history.forEach(p -> audience.sendMessage(config.message(
                            "history.entry",
                            Placeholder.unparsed("type", p.type().name()),
                            Placeholder.unparsed("id", p.id().toString()),
                            config.reason(p.reason()),
                            Placeholder.unparsed(
                                    "revoked",
                                    p.revoked()
                                            ? config.raw("history.revoked-suffix")
                                            : p.expired() ? config.raw("history.expired-suffix") : ""))));
                }));
    }
}
