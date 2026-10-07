package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.PunishmentPlugin;
import com.bradenkennedy.punishment.PunishmentDetails;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class BanListener implements Listener {

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        PunishmentPlugin.getDataRepository().findActive(event.getUniqueId(), PunishmentType.BAN)
                .ifPresent(ban -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen(ban)));
    }

    public static void kick(Punishment ban) {
        Bukkit.getScheduler().runTask(PunishmentPlugin.getInstance(), () -> {
            Player player = Bukkit.getPlayer(ban.target());
            if (player != null) {
                player.kickPlayer(screen(ban));
            }
        });
    }

    private static String screen(Punishment ban) {
        var config = PunishmentPlugin.getPluginConfig();
        return LegacyComponentSerializer.legacySection()
                .serialize(config.message("ban.screen", config.reason(ban.reason()), config.remaining(ban.expiry()),
                        PunishmentDetails.resolvers(ban)));
    }
}
