package com.bradenkennedy.punishment.command;

import static com.bradenkennedy.punishment.command.CommandSupport.issuerId;
import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyFlagYieldingStringParser;
import static org.incendo.cloud.parser.standard.UUIDParser.uuidParser;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.listener.WarnListener;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public final class WarnCommands {

    private final CommandSupport support;
    private final PunishmentRepository repository;
    private final WarnListener warnListener;

    public WarnCommands(CommandSupport support, PunishmentRepository repository, WarnListener warnListener) {
        this.support = support;
        this.repository = repository;
        this.warnListener = warnListener;
    }

    public void register(CommandManager<CommandSender> manager) {
        var silent = manager.flagBuilder("silent").withAliases("s");
        manager.command(manager.commandBuilder("warn")
                .required("player", offlinePlayerParser())
                .required("reason", greedyFlagYieldingStringParser())
                .flag(silent)
                .permission("punishments.warn")
                .handler(this::warn));
        manager.command(manager.commandBuilder("unwarn")
                .required("player", offlinePlayerParser())
                .optional("id", uuidParser())
                .optional("reason", greedyFlagYieldingStringParser())
                .flag(silent)
                .permission("punishments.unwarn")
                .handler(this::unwarn));
    }

    private void warn(CommandContext<CommandSender> ctx) {
        OfflinePlayer target = ctx.get("player");
        String reason = ctx.get("reason");
        var warning = new Punishment(
                UUID.randomUUID(),
                target.getUniqueId(),
                PunishmentType.WARN,
                new PunishmentIssuer(issuerId(ctx.sender()), Instant.now()),
                reason,
                null,
                false);
        if (!support.tryPunish(ctx, target, warning)) {
            return;
        }
        support.announce(ctx, "warn.announce", target);
        if (target.getPlayer() != null) {
            warnListener.deliverPending(target.getPlayer());
        }
    }

    private void unwarn(CommandContext<CommandSender> ctx) {
        OfflinePlayer target = ctx.get("player");
        Optional<UUID> id = ctx.optional("id");
        repository.findHistory(target.getUniqueId()).stream()
                .filter(p -> p.type() == PunishmentType.WARN && !p.revoked())
                .filter(p -> id.map(p.id()::equals).orElse(true))
                .max(Comparator.comparing(p -> p.issuer().issuedAt()))
                .ifPresentOrElse(
                        warning -> {
                            if (support.tryRevoke(ctx, target, warning)) {
                                support.announce(ctx, "unwarn.announce", target);
                            }
                        },
                        () -> support.reply(ctx, "unwarn.not-found", target));
    }
}
