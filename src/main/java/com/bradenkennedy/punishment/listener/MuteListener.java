package com.bradenkennedy.punishment.listener;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class MuteListener implements Listener {

    private final Set<String> blockedCommands;
    private final PunishmentRepository repository;
    private final PluginConfig config;
    private final BukkitAudiences audiences;

    public MuteListener(
            Collection<String> blockedCommands,
            PunishmentRepository repository,
            PluginConfig config,
            BukkitAudiences audiences) {
        this.repository = repository;
        this.config = config;
        this.audiences = audiences;
        this.blockedCommands =
                blockedCommands.stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
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
        return repository
                .findActive(player.getUniqueId(), PunishmentType.MUTE)
                .map(mute -> {
                    audiences
                            .player(player)
                            .sendMessage(config.message("mute.notice", config.remaining(mute.expiry())));
                    return true;
                })
                .orElse(false);
    }
}
