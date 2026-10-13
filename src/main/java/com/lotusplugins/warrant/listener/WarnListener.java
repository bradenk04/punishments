package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class WarnListener implements Listener {

    private final PunishmentRepository repository;
    private final PluginConfig config;
    private final BukkitAudiences audiences;

    public WarnListener(PunishmentRepository repository, PluginConfig config, BukkitAudiences audiences) {
        this.repository = repository;
        this.config = config;
        this.audiences = audiences;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        deliverPending(event.getPlayer());
    }

    public void deliverPending(Player player) {
        repository.findUnacknowledgedWarnings(player.getUniqueId()).forEach(warning -> {
            audiences.player(player).sendMessage(config.message("warn.notice", config.reason(warning.reason())));
            repository.acknowledge(warning.id());
        });
    }
}
