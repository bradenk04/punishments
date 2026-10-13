package com.lotusplugins.warrant.storage;

import com.lotusplugins.warrant.api.model.HistoryPage;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PunishmentRepository {
    boolean create(Punishment punishment);

    boolean createImported(Punishment punishment, String source, String sourceId);

    boolean hasImported(String source, String sourceId);

    void revoke(UUID punishmentId, UUID revokedBy, String reason, Instant atTime);

    Optional<Punishment> findActive(UUID player, PunishmentType type);

    List<Punishment> findHistory(UUID player);

    HistoryPage findHistory(UUID player, Optional<PunishmentType> type, int offset, int limit);

    List<Punishment> findUnacknowledgedWarnings(UUID player);

    void acknowledge(UUID punishmentId);
}
