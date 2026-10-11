package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.ActivePunishmentCache;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CacheLoadListener implements Listener {

    private final PunishmentRepository repository;
    private final ActivePunishmentCache cache;

    public CacheLoadListener(PunishmentRepository repository, ActivePunishmentCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        cache.evict(event.getUniqueId());
        repository.findActive(event.getUniqueId(), PunishmentType.MUTE).ifPresent(cache::put);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.evict(event.getPlayer().getUniqueId());
    }
}
