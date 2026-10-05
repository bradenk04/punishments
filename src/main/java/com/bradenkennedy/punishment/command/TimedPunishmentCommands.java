package com.bradenkennedy.punishment.command;

import com.bradenkennedy.punishment.PunishmentPlugin;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.command.parser.DurationParser;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static com.bradenkennedy.punishment.command.CommandSupport.announce;
import static com.bradenkennedy.punishment.command.CommandSupport.isExempt;
import static com.bradenkennedy.punishment.command.CommandSupport.issuerId;
import static com.bradenkennedy.punishment.command.CommandSupport.reply;
import static com.bradenkennedy.punishment.command.parser.DurationParser.durationParser;
import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyFlagYieldingStringParser;

public final class TimedPunishmentCommands {

    private TimedPunishmentCommands() {
    }

    public static void register(CommandManager<CommandSender> manager, PunishmentType type, Consumer<Punishment> enforce) {
        String name = type.name().toLowerCase();
        var silent = manager.flagBuilder("silent").withAliases("s");
        manager.command(manager.commandBuilder(name)
                .required("player", offlinePlayerParser())
                .optional("reason", greedyFlagYieldingStringParser())
                .flag(silent)
                .permission("punishments." + name)
                .handler(ctx -> issue(ctx, type, null, enforce)));
        manager.command(manager.commandBuilder("temp" + name)
                .required("player", offlinePlayerParser())
                .required("duration", durationParser())
                .optional("reason", greedyFlagYieldingStringParser())
                .flag(silent)
                .permission("punishments.temp" + name)
                .handler(ctx -> issue(ctx, type, ctx.get("duration"), enforce)));
        manager.command(manager.commandBuilder("un" + name)
                .required("player", offlinePlayerParser())
                .flag(silent)
                .permission("punishments.un" + name)
                .handler(ctx -> revoke(ctx, type)));
    }

    private static void issue(CommandContext<CommandSender> ctx, PunishmentType type, @Nullable Duration duration, Consumer<Punishment> enforce) {
        OfflinePlayer target = ctx.get("player");
        String name = type.name().toLowerCase();
        var repository = PunishmentPlugin.getDataRepository();
        if (isExempt(target)) {
            reply(ctx, "exempt", target);
            return;
        }
        if (repository.findActive(target.getUniqueId(), type).isPresent()) {
            reply(ctx, name + ".already-active", target);
            return;
        }
        Instant now = Instant.now();
        String reason = ctx.<String>optional("reason").orElse(null);
        var punishment = new Punishment(UUID.randomUUID(), target.getUniqueId(), type,
                new PunishmentIssuer(issuerId(ctx.sender()), now), reason,
                duration == null ? null : now.plus(duration), false);
        repository.create(punishment);
        var config = PunishmentPlugin.getPluginConfig();
        announce(ctx, name + ".announce", target,
                Placeholder.unparsed("duration", duration == null ? config.raw("permanent-duration") : DurationParser.format(duration)),
                config.reason(reason));
        enforce.accept(punishment);
    }

    private static void revoke(CommandContext<CommandSender> ctx, PunishmentType type) {
        OfflinePlayer target = ctx.get("player");
        String name = "un" + type.name().toLowerCase();
        var repository = PunishmentPlugin.getDataRepository();
        repository.findActive(target.getUniqueId(), type).ifPresentOrElse(
                active -> {
                    repository.revoke(active.id(), issuerId(ctx.sender()), null, Instant.now());
                    announce(ctx, name + ".announce", target);
                },
                () -> reply(ctx, name + ".not-active", target));
    }
}
