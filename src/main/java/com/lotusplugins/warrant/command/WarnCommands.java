package com.lotusplugins.warrant.command;

import static com.lotusplugins.warrant.command.CommandSupport.issuerId;
import static com.lotusplugins.warrant.command.parser.ReasonParser.reasonParser;
import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;
import static org.incendo.cloud.parser.standard.UUIDParser.uuidParser;

import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentIssuer;
import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.listener.WarnListener;
import com.lotusplugins.warrant.storage.PunishmentRepository;
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
                .required("reason", reasonParser())
                .flag(silent)
                .permission("warrant.warn")
                .handler(ctx -> support.execute(ctx, () -> warn(ctx))));
        manager.command(manager.commandBuilder("unwarn")
                .required("player", offlinePlayerParser())
                .optional("id", uuidParser())
                .optional("reason", reasonParser())
                .flag(silent)
                .permission("warrant.unwarn")
                .handler(ctx -> support.execute(ctx, () -> unwarn(ctx))));
    }

    private void warn(CommandContext<CommandSender> ctx) {
        OfflinePlayer target = ctx.get("player");
        if (support.isExempt(target)) {
            support.reply(ctx, "exempt", target);
            return;
        }
        String reason = ctx.get("reason");
        var warning = new Punishment(
                UUID.randomUUID(),
                target.getUniqueId(),
                PunishmentType.WARN,
                new PunishmentIssuer(issuerId(ctx.sender()), Instant.now()),
                reason,
                null,
                null);
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
