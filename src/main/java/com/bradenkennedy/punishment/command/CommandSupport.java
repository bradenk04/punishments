package com.bradenkennedy.punishment.command;

import com.bradenkennedy.punishment.PunishmentPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;

import java.util.Objects;
import java.util.UUID;

final class CommandSupport {

    private static final UUID CONSOLE_ID = new UUID(0, 0);
    private static final String NOTIFY_PERMISSION = "punishments.notify";

    private CommandSupport() {
    }

    static void reply(CommandContext<CommandSender> ctx, String messageKey, OfflinePlayer target) {
        PunishmentPlugin.getAdventure().sender(ctx.sender())
                .sendMessage(PunishmentPlugin.getPluginConfig().message(messageKey, Placeholder.unparsed("name", nameOf(target))));
    }

    static void announce(CommandContext<CommandSender> ctx, String messageKey, OfflinePlayer target, TagResolver... extra) {
        boolean silent = ctx.flags().isPresent("silent");
        var resolver = TagResolver.builder()
                .resolver(Placeholder.unparsed("name", nameOf(target)))
                .resolver(Placeholder.unparsed("staff", ctx.sender().getName()))
                .resolvers(extra)
                .build();
        var config = PunishmentPlugin.getPluginConfig();
        var message = config.message(messageKey, resolver);
        PunishmentPlugin.getAdventure()
                .filter(s -> !silent || s == ctx.sender() || s.hasPermission(NOTIFY_PERMISSION))
                .sendMessage(silent ? config.message("silent-prefix").append(message) : message);
    }

    static String nameOf(OfflinePlayer player) {
        return Objects.requireNonNullElse(player.getName(), player.getUniqueId().toString());
    }

    static UUID issuerId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE_ID;
    }
}
