package com.bradenkennedy.punishment.command;

import com.bradenkennedy.punishment.PunishmentPlugin;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.listener.WarnListener;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import static com.bradenkennedy.punishment.command.CommandSupport.announce;
import static com.bradenkennedy.punishment.command.CommandSupport.issuerId;
import static com.bradenkennedy.punishment.command.CommandSupport.reply;
import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyFlagYieldingStringParser;
import static org.incendo.cloud.parser.standard.UUIDParser.uuidParser;

public final class WarnCommands {

    private WarnCommands() {
    }

    public static void register(CommandManager<CommandSender> manager) {
        var silent = manager.flagBuilder("silent").withAliases("s");
        manager.command(manager.commandBuilder("warn")
                .required("player", offlinePlayerParser())
                .required("reason", greedyFlagYieldingStringParser())
                .flag(silent)
                .permission("punishments.warn")
                .handler(WarnCommands::warn));
        manager.command(manager.commandBuilder("unwarn")
                .required("player", offlinePlayerParser())
                .optional("id", uuidParser())
                .flag(silent)
                .permission("punishments.unwarn")
                .handler(WarnCommands::unwarn));
    }

    private static void warn(CommandContext<CommandSender> ctx) {
        OfflinePlayer target = ctx.get("player");
        String reason = ctx.get("reason");
        PunishmentPlugin.getDataRepository().create(new Punishment(UUID.randomUUID(), target.getUniqueId(),
                PunishmentType.WARN, new PunishmentIssuer(issuerId(ctx.sender()), Instant.now()), reason, null, false));
        announce(ctx, "warn.announce", target, PunishmentPlugin.getPluginConfig().reason(reason));
        if (target.getPlayer() != null) {
            WarnListener.deliverPending(target.getPlayer());
        }
    }

    private static void unwarn(CommandContext<CommandSender> ctx) {
        OfflinePlayer target = ctx.get("player");
        Optional<UUID> id = ctx.optional("id");
        var repository = PunishmentPlugin.getDataRepository();
        repository.findHistory(target.getUniqueId()).stream()
                .filter(p -> p.type() == PunishmentType.WARN && !p.revoked())
                .filter(p -> id.map(p.id()::equals).orElse(true))
                .max(Comparator.comparing(p -> p.issuer().issuedAt()))
                .ifPresentOrElse(
                        warning -> {
                            repository.revoke(warning.id(), issuerId(ctx.sender()), null, Instant.now());
                            announce(ctx, "unwarn.announce", target);
                        },
                        () -> reply(ctx, "unwarn.not-found", target));
    }
}
