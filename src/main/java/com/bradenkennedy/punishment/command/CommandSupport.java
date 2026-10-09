package com.bradenkennedy.punishment.command;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.api.events.PlayerPunishedEvent;
import com.bradenkennedy.punishment.api.events.PlayerPunishmentRevokedEvent;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.ActivePunishmentCache;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import com.bradenkennedy.punishment.storage.StorageException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

public final class CommandSupport {

    private static final UUID CONSOLE_ID = new UUID(0, 0);
    private static final String NOTIFY_PERMISSION = "punishments.notify";
    private static final String EXEMPT_PERMISSION = "punishments.exempt";

    private final PunishmentRepository repository;
    private final ActivePunishmentCache cache;
    private final PluginConfig config;
    private final BukkitAudiences audiences;
    private final Logger logger;

    public CommandSupport(
            PunishmentRepository repository,
            ActivePunishmentCache cache,
            PluginConfig config,
            BukkitAudiences audiences,
            Logger logger) {
        this.repository = repository;
        this.cache = cache;
        this.config = config;
        this.audiences = audiences;
        this.logger = logger;
    }

    public void execute(CommandContext<CommandSender> ctx, Runnable action) {
        try {
            action.run();
        } catch (StorageException e) {
            logger.log(Level.SEVERE, "Punishment command failed", e);
            audiences.sender(ctx.sender()).sendMessage(config.message("storage-error"));
        }
    }

    void reply(CommandContext<CommandSender> ctx, String messageKey, OfflinePlayer target) {
        audiences
                .sender(ctx.sender())
                .sendMessage(config.message(messageKey, Placeholder.unparsed("name", nameOf(target))));
    }

    void announce(CommandContext<CommandSender> ctx, String messageKey, OfflinePlayer target, TagResolver... extra) {
        boolean silent = ctx.flags().isPresent("silent");
        var resolver = TagResolver.builder()
                .resolver(Placeholder.unparsed("name", nameOf(target)))
                .resolver(Placeholder.unparsed("staff", ctx.sender().getName()))
                .resolver(config.reason(reasonOf(ctx)))
                .resolvers(extra)
                .build();
        var message = config.message(messageKey, resolver);
        audiences
                .filter(s -> !silent || s == ctx.sender() || s.hasPermission(NOTIFY_PERMISSION))
                .sendMessage(silent ? config.message("silent-prefix").append(message) : message);
    }

    synchronized boolean tryPunish(CommandContext<CommandSender> ctx, OfflinePlayer target, Punishment punishment) {
        if (cancelled(ctx, target, new PlayerPunishedEvent(punishment))) {
            return false;
        }
        if (!repository.create(punishment)) {
            reply(ctx, punishment.type().name().toLowerCase(java.util.Locale.ROOT) + ".already-active", target);
            return false;
        }
        if (punishment.type() == PunishmentType.MUTE) {
            cache.put(punishment);
        }
        return true;
    }

    synchronized boolean tryRevoke(CommandContext<CommandSender> ctx, OfflinePlayer target, Punishment punishment) {
        if (cancelled(ctx, target, new PlayerPunishmentRevokedEvent(punishment))) {
            return false;
        }
        repository.revoke(punishment.id(), issuerId(ctx.sender()), reasonOf(ctx), Instant.now());
        if (punishment.type() == PunishmentType.MUTE) {
            cache.remove(punishment.target(), punishment.type());
        }
        return true;
    }

    private <T extends Event & Cancellable> boolean cancelled(
            CommandContext<CommandSender> ctx, OfflinePlayer target, T event) {
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            reply(ctx, "cancelled", target);
        }
        return event.isCancelled();
    }

    static @Nullable String reasonOf(CommandContext<CommandSender> ctx) {
        return ctx.<String>optional("reason").orElse(null);
    }

    static String nameOf(OfflinePlayer player) {
        return Objects.requireNonNullElse(player.getName(), player.getUniqueId().toString());
    }

    static boolean isExempt(OfflinePlayer target) {
        Player online = target.getPlayer();
        return target.isOp() || (online != null && online.hasPermission(EXEMPT_PERMISSION));
    }

    static UUID issuerId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE_ID;
    }
}
