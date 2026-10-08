package com.bradenkennedy.punishment.storage;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PunishmentRepository {
    void create(Punishment punishment);

    void revoke(UUID punishmentId, UUID revokedBy, String reason, Instant atTime);

    Optional<Punishment> findActive(UUID player, PunishmentType type);

    List<Punishment> findHistory(UUID player);

    List<Punishment> findUnacknowledgedWarnings(UUID player);

    void acknowledge(UUID punishmentId);
}
