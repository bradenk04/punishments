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
        @NotNull Boolean revoked) {
}
