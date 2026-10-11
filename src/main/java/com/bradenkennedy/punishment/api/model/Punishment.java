package com.bradenkennedy.punishment.api.model;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Punishment(
        @NotNull UUID id,
        @NotNull UUID target,
        @NotNull PunishmentType type,
        @NotNull PunishmentIssuer issuer,
        @Nullable String reason,
        @Nullable Instant expiry,
        @Nullable Revocation revocation) {

    public boolean expired() {
        return expiry != null && expiry.isBefore(Instant.now());
    }

    public boolean revoked() {
        return revocation != null;
    }
}
