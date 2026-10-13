package com.bradenkennedy.punishment.exemption;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface OfflinePermissionLookup {

    OfflinePermissionLookup NONE = (playerId, permission) -> CompletableFuture.completedFuture(false);

    CompletableFuture<Boolean> has(UUID playerId, String permission);
}
