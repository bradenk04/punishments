package com.bradenkennedy.punishment.exemption;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.OfflinePlayer;

public final class ExemptionCheck {

    static final String PERMISSION = "punishments.exempt";

    private final OfflinePermissionLookup offlineLookup;
    private final Executor mainThread;

    public ExemptionCheck(OfflinePermissionLookup offlineLookup, Executor mainThread) {
        this.offlineLookup = offlineLookup;
        this.mainThread = mainThread;
    }

    public CompletableFuture<Boolean> isExempt(OfflinePlayer target) {
        return CompletableFuture.supplyAsync(
                        () -> {
                            Optional<Boolean> onlinePermission = Optional.ofNullable(target.getPlayer())
                                    .map(online -> online.hasPermission(PERMISSION));
                            return isExempt(target.getUniqueId(), onlinePermission);
                        },
                        mainThread)
                .thenCompose(result -> result);
    }

    CompletableFuture<Boolean> isExempt(UUID playerId, Optional<Boolean> onlinePermission) {
        if (onlinePermission.isPresent()) {
            return CompletableFuture.completedFuture(onlinePermission.get());
        }
        return offlineLookup.has(playerId, PERMISSION);
    }
}
