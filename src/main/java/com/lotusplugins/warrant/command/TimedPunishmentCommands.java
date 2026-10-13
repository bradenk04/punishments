package com.lotusplugins.warrant.command;

import static com.lotusplugins.warrant.command.CommandSupport.issuerId;
import static com.lotusplugins.warrant.command.CommandSupport.reasonOf;
import static com.lotusplugins.warrant.command.parser.DurationParser.durationParser;
import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyFlagYieldingStringParser;

import com.lotusplugins.warrant.PluginConfig;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentIssuer;
import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.command.parser.DurationParser;
import com.lotusplugins.warrant.storage.PunishmentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

public final class TimedPunishmentCommands {

    private final CommandSupport support;
    private final PunishmentRepository repository;
    private final PluginConfig config;

    public TimedPunishmentCommands(CommandSupport support, PunishmentRepository repository, PluginConfig config) {
        this.support = support;
        this.repository = repository;
        this.config = config;
    }

    public void register(CommandManager<CommandSender> manager, PunishmentType type, Consumer<Punishment> enforce) {
        String name = type.name().toLowerCase();
        var silent = manager.flagBuilder("silent").withAliases("s");
        manager.command(manager.commandBuilder(name)
                .required("player", offlinePlayerParser())
                .optional("reason", greedyFlagYieldingStringParser())
                .flag(silent)
                .permission("warrant." + name)
                .handler(ctx -> support.execute(ctx, () -> issue(ctx, type, null, enforce))));
        manager.command(manager.commandBuilder("temp" + name)
                .required("player", offlinePlayerParser())
                .required("duration", durationParser())
                .optional("reason", greedyFlagYieldingStringParser())
                .flag(silent)
                .permission("warrant.temp" + name)
                .handler(ctx -> support.execute(ctx, () -> issue(ctx, type, ctx.get("duration"), enforce))));
        manager.command(manager.commandBuilder("un" + name)
                .required("player", offlinePlayerParser())
                .flag(silent)
                .optional("reason", greedyFlagYieldingStringParser())
                .permission("warrant.un" + name)
                .handler(ctx -> support.execute(ctx, () -> revoke(ctx, type))));
    }

    private void issue(
            CommandContext<CommandSender> ctx,
            PunishmentType type,
            @Nullable Duration duration,
            Consumer<Punishment> enforce) {
        OfflinePlayer target = ctx.get("player");
        String name = type.name().toLowerCase();
        if (support.isExempt(target)) {
            support.reply(ctx, "exempt", target);
            return;
        }
        if (repository.findActive(target.getUniqueId(), type).isPresent()) {
            support.reply(ctx, name + ".already-active", target);
            return;
        }
        Instant now = Instant.now();
        var punishment = new Punishment(
                UUID.randomUUID(),
                target.getUniqueId(),
                type,
                new PunishmentIssuer(issuerId(ctx.sender()), now),
                reasonOf(ctx),
                duration == null ? null : now.plus(duration),
                null);
        if (!support.tryPunish(ctx, target, punishment)) {
            return;
        }
        support.announce(
                ctx,
                name + ".announce",
                target,
                Placeholder.unparsed(
                        "duration",
                        duration == null ? config.raw("permanent-duration") : DurationParser.format(duration)));
        enforce.accept(punishment);
    }

    private void revoke(CommandContext<CommandSender> ctx, PunishmentType type) {
        OfflinePlayer target = ctx.get("player");
        String name = "un" + type.name().toLowerCase();
        repository
                .findActive(target.getUniqueId(), type)
                .ifPresentOrElse(
                        active -> {
                            if (support.tryRevoke(ctx, target, active)) {
                                support.announce(ctx, name + ".announce", target);
                            }
                        },
                        () -> support.reply(ctx, name + ".not-active", target));
    }
}
