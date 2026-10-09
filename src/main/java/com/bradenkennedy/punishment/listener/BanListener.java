package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import com.bradenkennedy.punishment.storage.StorageException;
import java.util.logging.Level;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;

public final class BanListener implements Listener {

    private final Plugin plugin;
    private final PunishmentRepository repository;
    private final PluginConfig config;

    public BanListener(Plugin plugin, PunishmentRepository repository, PluginConfig config) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            repository
                    .findActive(event.getUniqueId(), PunishmentType.BAN)
                    .ifPresent(ban -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen(ban)));
        } catch (StorageException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not check login ban", e);
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    LegacyComponentSerializer.legacySection().serialize(config.message("storage-unavailable")));
        }
    }

    public void kick(Punishment ban) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(ban.target());
            if (player != null) {
                player.kickPlayer(screen(ban));
            }
        });
    }

    private String screen(Punishment ban) {
        return LegacyComponentSerializer.legacySection()
                .serialize(config.message("ban.screen", config.reason(ban.reason()), config.remaining(ban.expiry())));
    }
}
