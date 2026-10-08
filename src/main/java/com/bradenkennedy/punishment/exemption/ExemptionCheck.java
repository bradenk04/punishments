package com.bradenkennedy.punishment.exemption;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.OfflinePlayer;

public final class ExemptionCheck {

    static final String PERMISSION = "punishments.exempt";

    private final OfflinePermissionLookup offlineLookup;

    public ExemptionCheck(OfflinePermissionLookup offlineLookup) {
        this.offlineLookup = offlineLookup;
    }

    public CompletableFuture<Boolean> isExempt(OfflinePlayer target) {
        Optional<Boolean> onlinePermission =
                Optional.ofNullable(target.getPlayer()).map(online -> online.hasPermission(PERMISSION));
        return isExempt(target.getUniqueId(), target.isOp(), onlinePermission);
    }

    CompletableFuture<Boolean> isExempt(UUID playerId, boolean operator, Optional<Boolean> onlinePermission) {
        if (operator) {
            return CompletableFuture.completedFuture(true);
        }
        if (onlinePermission.isPresent()) {
            return CompletableFuture.completedFuture(onlinePermission.get());
        }
        return offlineLookup.has(playerId, PERMISSION);
    }
}
