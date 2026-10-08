package com.bradenkennedy.punishment.storage;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActivePunishmentCache {

    private final Map<UUID, Map<PunishmentType, Punishment>> byPlayer = new ConcurrentHashMap<>();

    public Optional<Punishment> find(UUID player, PunishmentType type) {
        var punishments = byPlayer.get(player);
        if (punishments == null) {
            return Optional.empty();
        }
        var punishment = punishments.get(type);
        if (punishment == null) {
            return Optional.empty();
        }
        if (punishment.expired()) {
            punishments.remove(type, punishment);
            return Optional.empty();
        }
        return Optional.of(punishment);
    }

    public void put(Punishment punishment) {
        byPlayer.computeIfAbsent(punishment.target(), player -> new ConcurrentHashMap<>())
                .put(punishment.type(), punishment);
    }

    public void remove(UUID player, PunishmentType type) {
        var punishments = byPlayer.get(player);
        if (punishments != null) {
            punishments.remove(type);
        }
    }

    public void evict(UUID player) {
        byPlayer.remove(player);
    }
}
