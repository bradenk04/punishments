package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.PunishmentPlugin;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.command.parser.DurationParser;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public final class MuteListener implements Listener {

    private final Set<String> blockedCommands;

    public MuteListener(Collection<String> blockedCommands) {
        this.blockedCommands = blockedCommands.stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        event.setCancelled(isMuted(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        String unnamespaced = label.substring(label.indexOf(':') + 1);
        event.setCancelled(blockedCommands.contains(unnamespaced) && isMuted(event.getPlayer()));
    }

    private boolean isMuted(Player player) {
        return PunishmentPlugin.getDataRepository().findActive(player.getUniqueId(), PunishmentType.MUTE)
                .map(mute -> {
                    var config = PunishmentPlugin.getPluginConfig();
                    String remaining = mute.expiry() == null ? config.raw("permanent-duration") : DurationParser.format(Duration.between(Instant.now(), mute.expiry()));
                    PunishmentPlugin.getAdventure().player(player)
                            .sendMessage(config.message("mute.notice", Placeholder.unparsed("remaining", remaining)));
                    return true;
                })
                .orElse(false);
    }
}
