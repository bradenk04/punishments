package com.bradenkennedy.punishment.exemption;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;

final class VaultLookup implements OfflinePermissionLookup {

    private final Permission vault;
    private final Executor mainThread;

    VaultLookup(Permission vault, Executor mainThread) {
        this.vault = vault;
        this.mainThread = mainThread;
    }

    @Override
    public CompletableFuture<Boolean> has(UUID playerId, String permission) {
        return CompletableFuture.supplyAsync(
                () -> vault.playerHas(null, Bukkit.getOfflinePlayer(playerId), permission), mainThread);
    }
}
