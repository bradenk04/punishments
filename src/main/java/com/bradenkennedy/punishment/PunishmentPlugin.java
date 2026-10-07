package com.bradenkennedy.punishment;

import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.command.KickCommands;
import com.bradenkennedy.punishment.command.ImportCommands;
import com.bradenkennedy.punishment.command.PunishmentCommands;
import com.bradenkennedy.punishment.command.TimedPunishmentCommands;
import com.bradenkennedy.punishment.command.WarnCommands;
import com.bradenkennedy.punishment.listener.BanListener;
import com.bradenkennedy.punishment.listener.MuteListener;
import com.bradenkennedy.punishment.listener.WarnListener;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import com.bradenkennedy.punishment.network.NetworkCache;
import com.bradenkennedy.punishment.listener.NetworkListener;
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
    private NetworkCache networkCache;
    public NetworkCache networkCache() { return networkCache; }

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

        PunishmentPlugin.pluginConfig = new PluginConfig(this);
        try {
            String url = getConfig().getString("database.url", "");
            PunishmentPlugin.dataRepository = url.isBlank() ? new H2PunishmentRepository(this.getDataFolder())
                : new JdbcPunishmentRepository(url, getConfig().getString("database.username", ""),
                    getConfig().getString("database.password", ""));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        networkCache = new NetworkCache((JdbcPunishmentRepository)dataRepository);
        var network = new NetworkListener(this, networkCache, (JdbcPunishmentRepository)dataRepository);
        getServer().getPluginManager().registerEvents(network, this);
        long ticks = Math.max(1, getConfig().getLong("network.poll-interval-ms", 1000) / 50);
        getServer().getScheduler().runTaskTimerAsynchronously(this, network::poll, 1, ticks);
        getServer().getPluginManager().registerEvents(new MuteListener(pluginConfig.blockedMuteCommands()), this);
        getServer().getPluginManager().registerEvents(new WarnListener(), this);
        getServer().getPluginManager().registerEvents(new BanListener(), this);

        var commandManager = LegacyPaperCommandManager.createNative(this, ExecutionCoordinator.asyncCoordinator());
        PunishmentCommands.register(commandManager);
        TimedPunishmentCommands.register(commandManager, PunishmentType.MUTE, mute -> {});
        TimedPunishmentCommands.register(commandManager, PunishmentType.BAN, BanListener::kick);
        WarnCommands.register(commandManager);
        KickCommands.register(commandManager);
        ImportCommands.register(commandManager);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (dataRepository instanceof AutoCloseable closeable) {
            try { closeable.close(); } catch (Exception failure) { getLogger().warning("Database shutdown failed"); }
        }
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
