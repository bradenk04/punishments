package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.PunishmentPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

public final class WarnListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        deliverPending(event.getPlayer());
    }

    public static void deliverPending(Player player) {
        var repository = PunishmentPlugin.getDataRepository();
        repository.findUnacknowledgedWarnings(player.getUniqueId()).forEach(warning -> {
            PunishmentPlugin.getAdventure().player(player).sendMessage(PunishmentPlugin.getMiniMessage()
                    .deserialize("<yellow>You have been warned: <reason>",
                            Placeholder.unparsed("reason", Objects.requireNonNullElse(warning.reason(), "No reason"))));
            repository.acknowledge(warning.id());
        });
    }
}
