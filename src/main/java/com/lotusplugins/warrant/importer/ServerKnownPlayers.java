package com.lotusplugins.warrant.importer;

import com.lotusplugins.warrant.Scheduling;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

public final class ServerKnownPlayers implements Supplier<Map<String, UUID>> {

    private final Plugin plugin;
    private final Scheduling scheduling;

    public ServerKnownPlayers(Plugin plugin, Scheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
    }

    @Override
    public Map<String, UUID> get() {
        try {
            var players = new CompletableFuture<Map<String, UUID>>();
            scheduling.global(() -> {
                try {
                    players.complete(snapshot());
                } catch (RuntimeException e) {
                    players.completeExceptionally(e);
                }
            });
            return players.get();
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
