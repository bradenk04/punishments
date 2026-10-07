package com.bradenkennedy.punishment.listener;
import com.bradenkennedy.punishment.PunishmentPlugin;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.network.NetworkCache;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkListener implements Listener {
    private final PunishmentPlugin plugin;
    private final NetworkCache cache;
    private final JdbcPunishmentRepository repository;
    private final Map<UUID,String> online = new ConcurrentHashMap<>();
    public NetworkListener(PunishmentPlugin plugin, NetworkCache cache, JdbcPunishmentRepository repository) {
        this.plugin = plugin; this.cache = cache; this.repository = repository;
        plugin.getServer().getOnlinePlayers().forEach(p -> online.put(p.getUniqueId(),p.getName()));
    }
    @EventHandler public void login(AsyncPlayerPreLoginEvent event) {
        try { cache.load(event.getUniqueId()); }
        catch (RuntimeException failure) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Punishment database unavailable. Please try again.");
        }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId(); String name = event.getPlayer().getName();
        online.put(id,name);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> { cache.load(id); repository.rememberName(id,name); });
    }
    @EventHandler public void quit(PlayerQuitEvent event) { UUID id = event.getPlayer().getUniqueId(); online.remove(id); cache.remove(id); }
    public void poll() {
        try {
            cache.poll(change -> {
                var punishment = repository.findById(change.punishmentId);
                plugin.getServer().getScheduler().runTask(plugin, () -> punishment.ifPresent(p -> {
                    var message = Component.text("[Punishments] " + change.action + " " + p.type() + " " + p.target() + " ID " + p.id());
                    plugin.getServer().getOnlinePlayers().stream().filter(s -> s.hasPermission("punishments.notify"))
                        .forEach(s -> plugin.adventure().player(s).sendMessage(message));
                    if (p.type() == PunishmentType.KICK && change.action.equals("ISSUE")) {
                        var player = plugin.getServer().getPlayer(p.target());
                        if (player != null) player.kickPlayer("Kicked: " + Objects.toString(p.reason(), "No reason") + "\nID: " + p.id());
                    }
                }));
            });
            // Refresh also catches changes made before this instance started, without replaying old kicks.
            for (UUID id : online.keySet()) {
                cache.load(id); cache.active(id,PunishmentType.BAN).ifPresent(BanListener::kick);
            }
        } catch (RuntimeException failure) { plugin.getLogger().warning("Network punishment refresh failed; retrying next interval"); }
    }
}
