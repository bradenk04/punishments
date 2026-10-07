package com.bradenkennedy.punishment.network;
import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import com.bradenkennedy.punishment.storage.model.NetworkChange;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
/** Shared changes-table polling, including transactions that commit out of order. */
public final class NetworkCache {
    private final JdbcPunishmentRepository repository;
    private final Map<UUID,List<Punishment>> cache = new ConcurrentHashMap<>();
    private final Set<UUID> seen = new HashSet<>();
    private final long started = System.currentTimeMillis();
    public NetworkCache(JdbcPunishmentRepository repository) { this.repository = repository; }
    public void load(UUID player) { cache.put(player, repository.findHistory(player)); }
    public void remove(UUID player) { cache.remove(player); }
    public Optional<Punishment> active(UUID player, PunishmentType type) {
        return cache.getOrDefault(player,List.of()).stream()
                .filter(p -> p.type() == type && !p.revoked() && !p.expired())
                .max(Comparator.comparing(p -> p.issuer().issuedAt()));
    }
    public synchronized void poll(Consumer<NetworkChange> notify) {
        for (var change : repository.changesSince(started)) {
            if (seen.contains(change.id)) continue;
            if (cache.containsKey(change.target)) load(change.target);
            notify.accept(change); seen.add(change.id);
        }
    }
}
