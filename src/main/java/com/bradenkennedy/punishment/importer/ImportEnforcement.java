package com.bradenkennedy.punishment.importer;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.storage.ActivePunishmentCache;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

public final class ImportEnforcement implements Consumer<Punishment> {

    private final Plugin plugin;
    private final ActivePunishmentCache cache;
    private final Consumer<Punishment> kickBanned;

    public ImportEnforcement(Plugin plugin, ActivePunishmentCache cache, Consumer<Punishment> kickBanned) {
        this.plugin = plugin;
        this.cache = cache;
        this.kickBanned = kickBanned;
    }

    @Override
    public void accept(Punishment punishment) {
        if (punishment.revoked() || punishment.expired()) return;
        switch (punishment.type()) {
            case BAN -> kickBanned.accept(punishment);
            case MUTE ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (plugin.getServer().getPlayer(punishment.target()) != null) cache.put(punishment);
                });
            case WARN, KICK -> {}
        }
    }
}
