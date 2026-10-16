package com.lotusplugins.warrant.listener;

import com.lotusplugins.warrant.PluginConfig;
import com.lotusplugins.warrant.PunishmentDetails;
import com.lotusplugins.warrant.Scheduling;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.storage.PunishmentRepository;
import com.lotusplugins.warrant.storage.StorageException;
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
    private final Scheduling scheduling;
    private final PunishmentRepository repository;
    private final PluginConfig config;

    public BanListener(Plugin plugin, Scheduling scheduling, PunishmentRepository repository, PluginConfig config) {
        this.plugin = plugin;
        this.scheduling = scheduling;
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
        scheduling.global(() -> {
            Player player = Bukkit.getPlayer(ban.target());
            if (player != null) {
                scheduling.onPlayer(player, () -> player.kickPlayer(screen(ban)));
            }
        });
    }

    private String screen(Punishment ban) {
        return LegacyComponentSerializer.legacySection()
                .serialize(config.message(
                        "ban.screen",
                        config.reason(ban.reason()),
                        config.remaining(ban.expiry()),
                        PunishmentDetails.resolvers(ban)));
    }
}
