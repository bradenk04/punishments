package com.bradenkennedy.punishment.exemption;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;

final class LuckPermsLookup implements OfflinePermissionLookup {

    private final LuckPerms luckPerms;

    LuckPermsLookup(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    @Override
    public CompletableFuture<Boolean> has(UUID playerId, String permission) {
        return luckPerms
                .getUserManager()
                .loadUser(playerId)
                .thenApply(user -> user.getCachedData()
                        .getPermissionData()
                        .checkPermission(permission)
                        .asBoolean());
    }
}
