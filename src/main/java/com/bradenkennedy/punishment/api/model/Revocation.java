package com.bradenkennedy.punishment.api.model;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Revocation(
        @NotNull UUID by, @Nullable String reason, @NotNull Instant at) {}
