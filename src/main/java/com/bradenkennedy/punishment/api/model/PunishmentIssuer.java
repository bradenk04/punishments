package com.bradenkennedy.punishment.api.model;

import java.time.Instant;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

public record PunishmentIssuer(
        @NotNull UUID issuer,
        @NotNull Instant issuedAt) {

}
