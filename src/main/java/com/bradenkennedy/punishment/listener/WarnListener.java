package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.PunishmentPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class WarnListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        deliverPending(event.getPlayer());
    }

    public static void deliverPending(Player player) {
        var repository = PunishmentPlugin.getDataRepository();
        repository.findUnacknowledgedWarnings(player.getUniqueId()).forEach(warning -> {
            var config = PunishmentPlugin.getPluginConfig();
            PunishmentPlugin.getAdventure().player(player).sendMessage(config.message("warn.notice", config.reason(warning.reason())));
            repository.acknowledge(warning.id());
        });
    }
}
