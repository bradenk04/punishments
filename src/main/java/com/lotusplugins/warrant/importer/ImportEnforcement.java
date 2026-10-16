package com.lotusplugins.warrant.importer;

import com.lotusplugins.warrant.Scheduling;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.storage.ActivePunishmentCache;
import java.util.function.Consumer;
import org.bukkit.Bukkit;

public final class ImportEnforcement implements Consumer<Punishment> {

    private final Scheduling scheduling;
    private final ActivePunishmentCache cache;
    private final Consumer<Punishment> kickBanned;

    public ImportEnforcement(Scheduling scheduling, ActivePunishmentCache cache, Consumer<Punishment> kickBanned) {
        this.scheduling = scheduling;
        this.cache = cache;
        this.kickBanned = kickBanned;
    }

    @Override
    public void accept(Punishment punishment) {
        if (punishment.revoked() || punishment.expired()) return;
        switch (punishment.type()) {
            case BAN -> kickBanned.accept(punishment);
            case MUTE ->
                scheduling.global(() -> {
                    if (Bukkit.getPlayer(punishment.target()) != null) cache.put(punishment);
                });
            case WARN, KICK -> {}
        }
    }
}
