package com.bradenkennedy.punishment.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;

public interface PunishmentRepository {
    void create(Punishment punishment);

    void revoke(UUID punishmentId, UUID revokedBy, String reason, Instant atTime);

    Optional<Punishment> findActive(UUID player, PunishmentType type);

    List<Punishment> findHistory(UUID player);
}
