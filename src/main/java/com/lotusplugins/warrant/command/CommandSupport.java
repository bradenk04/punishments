package com.lotusplugins.warrant.command;

import com.lotusplugins.warrant.PluginConfig;
import com.lotusplugins.warrant.api.events.PlayerPunishedEvent;
import com.lotusplugins.warrant.api.events.PlayerPunishmentRevokedEvent;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.command.parser.ReasonParser;
import com.lotusplugins.warrant.exemption.ExemptionCheck;
import com.lotusplugins.warrant.metrics.PunishmentCounter;
import com.lotusplugins.warrant.storage.ActivePunishmentCache;
import com.lotusplugins.warrant.storage.PunishmentRepository;
import com.lotusplugins.warrant.storage.StorageException;
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
    private static final String NOTIFY_PERMISSION = "warrant.notify";

    private final PunishmentRepository repository;
    private final ActivePunishmentCache cache;
    private final PluginConfig config;
    private final BukkitAudiences audiences;
    private final ExemptionCheck exemptions;
    private final PunishmentCounter counter;
    private final Logger logger;

    public CommandSupport(
            PunishmentRepository repository,
            ActivePunishmentCache cache,
            PluginConfig config,
            BukkitAudiences audiences,
            ExemptionCheck exemptions,
            PunishmentCounter counter,
            Logger logger) {
        this.repository = repository;
        this.cache = cache;
        this.config = config;
        this.audiences = audiences;
        this.exemptions = exemptions;
        this.counter = counter;
        this.logger = logger;
    }

    public void execute(CommandContext<? extends CommandSender> ctx, Runnable action) {
        try {
            action.run();
        } catch (StorageException e) {
            logger.log(Level.SEVERE, "Warrant command failed", e);
            audiences.sender(ctx.sender()).sendMessage(config.message("storage-error"));
        }
    }

    void reply(CommandContext<CommandSender> ctx, String messageKey, OfflinePlayer target) {
        audiences
                .sender(ctx.sender())
                .sendMessage(config.message(messageKey, Placeholder.unparsed("name", nameOf(target))));
    }

    void announce(CommandContext<CommandSender> ctx, String messageKey, OfflinePlayer target, TagResolver... extra) {
        boolean silent = ctx.flags().isPresent("silent") || ctx.contains(ReasonParser.LEADING_SILENT_KEY);
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
        counter.increment();
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

    boolean isExempt(OfflinePlayer target) {
        return exemptions.isExempt(target).join();
    }

    static UUID issuerId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE_ID;
    }
}
