package com.bradenkennedy.punishment;

import com.bradenkennedy.punishment.command.MuteCommands;
import com.bradenkennedy.punishment.command.PunishmentCommands;
import com.bradenkennedy.punishment.command.WarnCommands;
import com.bradenkennedy.punishment.listener.MuteListener;
import com.bradenkennedy.punishment.listener.WarnListener;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import java.sql.SQLException;

public class PunishmentPlugin extends JavaPlugin {

    private static PunishmentPlugin instance;
    private static PunishmentRepository dataRepository;
    private static PluginConfig pluginConfig;

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
        getServer().getPluginManager().registerEvents(new MuteListener(pluginConfig.blockedMuteCommands()), this);
        getServer().getPluginManager().registerEvents(new WarnListener(), this);

        var commandManager = LegacyPaperCommandManager.createNative(this, ExecutionCoordinator.asyncCoordinator());
        PunishmentCommands.register(commandManager);
        MuteCommands.register(commandManager);
        WarnCommands.register(commandManager);
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

    public static PunishmentRepository getDataRepository() { return dataRepository; }

    public static PluginConfig getPluginConfig() { return pluginConfig; }
}
