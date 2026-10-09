package com.bradenkennedy.punishment.command;

import static com.bradenkennedy.punishment.command.CommandSupport.isExempt;
import static com.bradenkennedy.punishment.command.CommandSupport.issuerId;
import static com.bradenkennedy.punishment.command.CommandSupport.reasonOf;
import static org.incendo.cloud.bukkit.parser.PlayerParser.playerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyFlagYieldingStringParser;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.PunishmentDetails;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public final class KickCommands {
    private final Plugin plugin;
    private final CommandSupport support;
    private final PluginConfig config;

    public KickCommands(Plugin plugin, CommandSupport support, PluginConfig config) {
        this.plugin = plugin;
        this.support = support;
        this.config = config;
    }

    public void register(CommandManager<CommandSender> manager) {
        manager.command(manager.commandBuilder("kick")
                .required("player", playerParser())
                .optional("reason", greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .permission("punishments.kick")
                .handler(this::kick));
    }

    private void kick(CommandContext<CommandSender> ctx) {
        Player target = ctx.get("player");
        if (isExempt(target)) {
            support.reply(ctx, "exempt", target);
            return;
        }
        var kick = new Punishment(
                UUID.randomUUID(),
                target.getUniqueId(),
                PunishmentType.KICK,
                new PunishmentIssuer(issuerId(ctx.sender()), Instant.now()),
                reasonOf(ctx),
                null,
                false);
        if (!support.tryPunish(ctx, target, kick)) {
            return;
        }
        support.announce(ctx, "kick.announce", target);
        String screen = LegacyComponentSerializer.legacySection()
                .serialize(config.message(
                        "kick.screen",
                        config.reason(kick.reason()),
                        PunishmentDetails.resolvers(kick, ctx.sender().getName())));
        Bukkit.getScheduler().runTask(plugin, () -> target.kickPlayer(screen));
    }
}
