package com.bradenkennedy.punishment.command;

import com.bradenkennedy.punishment.PunishmentPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;

import java.util.Objects;

import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;

public final class PunishmentCommands {

    private PunishmentCommands() {
    }

    public static void register(CommandManager<CommandSender> manager) {
        manager.command(manager.commandBuilder("punish")
                .literal("history")
                .required("player", offlinePlayerParser())
                .permission("punishments.history")
                .handler(ctx -> {
                    OfflinePlayer target = ctx.get("player");
                    Audience audience = PunishmentPlugin.getAdventure().sender(ctx.sender());
                    var history = PunishmentPlugin.getDataRepository().findHistory(target.getUniqueId());
                    if (history.isEmpty()) {
                        audience.sendMessage(PunishmentPlugin.getMiniMessage().deserialize("<gray>No punishments found for <white><name>",
                                Placeholder.unparsed("name", Objects.requireNonNullElse(target.getName(), target.getUniqueId().toString()))));
                    }
                    history.forEach(p -> audience.sendMessage(PunishmentPlugin.getMiniMessage().deserialize("<red><type></red> <dark_gray><id></dark_gray> <gray><reason><revoked>",
                            Placeholder.unparsed("type", p.type().name()),
                            Placeholder.unparsed("id", p.id().toString()),
                            Placeholder.unparsed("reason", Objects.requireNonNullElse(p.reason(), "No reason")),
                            Placeholder.unparsed("revoked", p.revoked() ? " (revoked)" : ""))));
                }));
    }
}
