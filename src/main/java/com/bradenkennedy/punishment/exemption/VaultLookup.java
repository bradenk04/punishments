package com.bradenkennedy.punishment.exemption;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;

final class VaultLookup implements OfflinePermissionLookup {

    private final Permission vault;

    VaultLookup(Permission vault) {
        this.vault = vault;
    }

    @Override
    public CompletableFuture<Boolean> has(UUID playerId, String permission) {
        return CompletableFuture.supplyAsync(
                () -> vault.playerHas(null, Bukkit.getOfflinePlayer(playerId), permission));
    }
}
