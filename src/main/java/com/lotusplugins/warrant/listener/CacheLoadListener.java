package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.ActivePunishmentCache;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import com.bradenkennedy.punishment.storage.StorageException;
import java.util.logging.Level;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class CacheLoadListener implements Listener {

    private final Plugin plugin;
    private final PluginConfig config;
    private final PunishmentRepository repository;
    private final ActivePunishmentCache cache;

    public CacheLoadListener(
            Plugin plugin, PunishmentRepository repository, ActivePunishmentCache cache, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.cache = cache;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        cache.evict(event.getUniqueId());
        try {
            repository.findActive(event.getUniqueId(), PunishmentType.MUTE).ifPresent(cache::put);
        } catch (StorageException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load login mute", e);
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    LegacyComponentSerializer.legacySection().serialize(config.message("storage-unavailable")));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.evict(event.getPlayer().getUniqueId());
    }
}
