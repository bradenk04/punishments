package com.bradenkennedy.punishment.exemption;

import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Server;

public final class OfflinePermissionLookups {

    private OfflinePermissionLookups() {}

    public static OfflinePermissionLookup detect(Server server) {
        var plugins = server.getPluginManager();
        var services = server.getServicesManager();
        if (plugins.isPluginEnabled("LuckPerms")) {
            var luckPerms = services.getRegistration(LuckPerms.class);
            if (luckPerms != null) {
                return new LuckPermsLookup(luckPerms.getProvider());
            }
        }
        if (plugins.isPluginEnabled("Vault")) {
            var vault = services.getRegistration(Permission.class);
            if (vault != null) {
                return new VaultLookup(vault.getProvider());
            }
        }
        return OfflinePermissionLookup.NONE;
    }
}
