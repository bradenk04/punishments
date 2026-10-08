package com.bradenkennedy.punishment;

import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.command.KickCommands;
import com.bradenkennedy.punishment.command.PunishmentCommands;
import com.bradenkennedy.punishment.command.TimedPunishmentCommands;
import com.bradenkennedy.punishment.command.WarnCommands;
import com.bradenkennedy.punishment.listener.BanListener;
import com.bradenkennedy.punishment.listener.CacheLoadListener;
import com.bradenkennedy.punishment.listener.MuteListener;
import com.bradenkennedy.punishment.listener.WarnListener;
import com.bradenkennedy.punishment.storage.ActivePunishmentCache;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import java.sql.SQLException;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

public class PunishmentPlugin extends JavaPlugin {

    private static PunishmentPlugin instance;
    private static PunishmentRepository dataRepository;
    private static PluginConfig pluginConfig;
    private static ActivePunishmentCache activePunishmentCache;

    private BukkitAudiences adventure;
    private MiniMessage miniMessage;

    public static PunishmentPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Punishments is not initialized yet!");
        }
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.adventure = BukkitAudiences.create(this);
        this.miniMessage = MiniMessage.miniMessage();

        try {
            PunishmentPlugin.dataRepository = new H2PunishmentRepository(this.getDataFolder());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        PunishmentPlugin.pluginConfig = new PluginConfig(this);
        PunishmentPlugin.activePunishmentCache = new ActivePunishmentCache();
        getServer().getPluginManager().registerEvents(new CacheLoadListener(dataRepository, activePunishmentCache), this);
        getServer().getPluginManager().registerEvents(new MuteListener(activePunishmentCache, pluginConfig.blockedMuteCommands()), this);
        getServer().getPluginManager().registerEvents(new WarnListener(), this);
        getServer().getPluginManager().registerEvents(new BanListener(), this);

        var commandManager = LegacyPaperCommandManager.createNative(this, ExecutionCoordinator.asyncCoordinator());
        PunishmentCommands.register(commandManager);
        TimedPunishmentCommands.register(commandManager, PunishmentType.MUTE, mute -> {});
        TimedPunishmentCommands.register(commandManager, PunishmentType.BAN, BanListener::kick);
        WarnCommands.register(commandManager);
        KickCommands.register(commandManager);
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        this.miniMessage = null;
        instance = null;
    }

    public BukkitAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access BukkitAudiences when Punishments was disabled!");
        }
        return this.adventure;
    }

    public MiniMessage miniMessage() {
        if (this.miniMessage == null) {
            this.miniMessage = MiniMessage.miniMessage();
        }
        return this.miniMessage;
    }

    public static BukkitAudiences getAdventure() {
        return getInstance().adventure();
    }

    public static MiniMessage getMiniMessage() {
        return getInstance().miniMessage();
    }

    public static PunishmentRepository getDataRepository() {
        return dataRepository;
    }

    public static ActivePunishmentCache getActivePunishmentCache() { return activePunishmentCache; }

    public static PluginConfig getPluginConfig() { return pluginConfig; }
}
