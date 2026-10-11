package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.network.NetworkCache;
import com.bradenkennedy.punishment.storage.ActivePunishmentCache;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class NetworkListener implements Listener {
    private final Plugin plugin;
    private final JdbcPunishmentRepository repository;
    private final NetworkCache changes;
    private final ActivePunishmentCache cache;
    private final BanListener bans;
    private final PluginConfig messages;
    private final BukkitAudiences audiences;
    private final Map<UUID, String> online = new ConcurrentHashMap<>();
    private final String unavailable;

    public NetworkListener(
            Plugin plugin,
            JdbcPunishmentRepository repository,
            NetworkCache changes,
            ActivePunishmentCache cache,
            BanListener bans,
            PluginConfig messages,
            BukkitAudiences audiences) {
        this.plugin = plugin;
        this.repository = repository;
        this.changes = changes;
        this.cache = cache;
        this.bans = bans;
        this.messages = messages;
        this.audiences = audiences;
        unavailable = LegacyComponentSerializer.legacySection().serialize(messages.message("network.unavailable"));
        plugin.getServer().getOnlinePlayers().forEach(p -> online.put(p.getUniqueId(), p.getName()));
    }

    @EventHandler
    public void login(AsyncPlayerPreLoginEvent event) {
        try {
            changes.load(event.getUniqueId());
        } catch (RuntimeException failure) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, unavailable);
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        online.put(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        online.remove(id);
        changes.remove(id);
    }

    public void poll() {
        try {
            changes.poll(change -> repository
                    .findById(change.punishmentId())
                    .ifPresent(p -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        var message = messages.message(
                                "network.change",
                                Placeholder.unparsed("action", change.action()),
                                Placeholder.unparsed("type", p.type().name()),
                                Placeholder.unparsed("target", p.target().toString()),
                                Placeholder.unparsed("id", p.id().toString()));
                        plugin.getServer().getOnlinePlayers().stream()
                                .filter(s -> s.hasPermission("punishments.notify"))
                                .forEach(s -> audiences.player(s).sendMessage(message));
                        if (p.type() == PunishmentType.KICK && change.action().equals("ISSUE")) {
                            var player = plugin.getServer().getPlayer(p.target());
                            if (player != null)
                                player.kickPlayer(LegacyComponentSerializer.legacySection()
                                        .serialize(messages.message(
                                                "network.kick",
                                                messages.reason(p.reason()),
                                                Placeholder.unparsed("id", p.id().toString()))));
                        }
                    })));
            for (var player : online.entrySet()) {
                repository.rememberName(player.getKey(), player.getValue());
                var mute = repository.findActive(player.getKey(), PunishmentType.MUTE);
                if (mute.isPresent()) cache.put(mute.get());
                else cache.remove(player.getKey(), PunishmentType.MUTE);
                repository.findActive(player.getKey(), PunishmentType.BAN).ifPresent(bans::kick);
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Network punishment refresh failed; retrying next interval");
        }
    }
}
