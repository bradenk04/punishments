package com.bradenkennedy.punishment.importer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

public final class ServerKnownPlayers implements Supplier<Map<String, UUID>> {

    private final Plugin plugin;

    public ServerKnownPlayers(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Map<String, UUID> get() {
        try {
            return plugin.getServer()
                    .getScheduler()
                    .callSyncMethod(plugin, this::snapshot)
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading known players", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not read known players", e.getCause());
        }
    }

    private Map<String, UUID> snapshot() {
        Map<String, UUID> players = new HashMap<>();
        for (OfflinePlayer player : plugin.getServer().getOfflinePlayers()) {
            if (player.getName() != null)
                players.putIfAbsent(player.getName().toLowerCase(Locale.ROOT), player.getUniqueId());
        }
        return players;
    }
}
