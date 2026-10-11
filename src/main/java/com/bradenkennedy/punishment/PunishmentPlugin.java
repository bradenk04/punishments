package com.bradenkennedy.punishment;

import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.command.CommandSupport;
import com.bradenkennedy.punishment.command.ImportCommands;
import com.bradenkennedy.punishment.command.KickCommands;
import com.bradenkennedy.punishment.command.PunishmentCommands;
import com.bradenkennedy.punishment.command.TimedPunishmentCommands;
import com.bradenkennedy.punishment.command.WarnCommands;
import com.bradenkennedy.punishment.exemption.ExemptionCheck;
import com.bradenkennedy.punishment.exemption.OfflinePermissionLookups;
import com.bradenkennedy.punishment.listener.BanListener;
import com.bradenkennedy.punishment.listener.CacheLoadListener;
import com.bradenkennedy.punishment.listener.MuteListener;
import com.bradenkennedy.punishment.listener.NetworkListener;
import com.bradenkennedy.punishment.listener.WarnListener;
import com.bradenkennedy.punishment.metrics.PluginMetrics;
import com.bradenkennedy.punishment.metrics.PunishmentCounter;
import com.bradenkennedy.punishment.network.NetworkCache;
import com.bradenkennedy.punishment.storage.ActivePunishmentCache;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

public class PunishmentPlugin extends JavaPlugin {

    private BukkitAudiences adventure;
    private AutoCloseable database;
    private volatile boolean ready;
    private boolean stopped;

    @Override
    public void onEnable() {
        this.adventure = BukkitAudiences.create(this);

        var config = new PluginConfig(this, MiniMessage.miniMessage());
        String url = getConfig().getString("database.url", "");
        String username = getConfig().getString("database.username", "");
        String password = getConfig().getString("database.password", "");
        long ticks = Math.max(1, getConfig().getLong("network.poll-interval-ms", 1000) / 50);
        String unavailable = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(config.message("network.unavailable"));
        getServer()
                .getPluginManager()
                .registerEvents(
                        new org.bukkit.event.Listener() {
                            @org.bukkit.event.EventHandler
                            public void login(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
                                if (!ready)
                                    event.disallow(
                                            org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                                            unavailable);
                            }
                        },
                        this);
        getServer()
                .getScheduler()
                .runTaskAsynchronously(this, () -> openDatabase(url, username, password, config, ticks));
    }

    private void openDatabase(String url, String username, String password, PluginConfig config, long ticks) {
        AutoCloseable opened = null;
        try {
            PunishmentRepository repository;
            java.util.Optional<NetworkCache> changes;
            if (url.isBlank()) {
                var local = new H2PunishmentRepository(getDataFolder());
                repository = local;
                opened = local.connectionSource;
                changes = java.util.Optional.empty();
            } else {
                if (!url.startsWith("jdbc:mysql:") && !url.startsWith("jdbc:postgresql:"))
                    throw new IllegalArgumentException("Shared storage requires MySQL or PostgreSQL");
                var shared = new JdbcPunishmentRepository(url, username, password);
                repository = shared;
                opened = shared;
                changes = java.util.Optional.of(new NetworkCache(shared));
            }
            synchronized (this) {
                if (stopped) {
                    closeDatabase(opened);
                    return;
                }
                database = opened;
                getServer().getScheduler().runTask(this, () -> initialize(repository, config, changes, url, ticks));
            }
        } catch (SQLException | RuntimeException failure) {
            if (opened != null) closeDatabase(opened);
            getLogger()
                    .severe(
                            "Punishment database initialization failed. Logins remain blocked; check the configuration and restart the plugin.");
        }
    }

    private void initialize(
            PunishmentRepository repository,
            PluginConfig config,
            java.util.Optional<NetworkCache> changes,
            String url,
            long ticks) {
        var cache = new ActivePunishmentCache();
        var banListener = new BanListener(this, repository, config);
        var warnListener = new WarnListener(repository, config, adventure);
        getServer().getPluginManager().registerEvents(new CacheLoadListener(repository, cache), this);
        getServer()
                .getPluginManager()
                .registerEvents(new MuteListener(cache, config.blockedMuteCommands(), config, adventure), this);
        getServer().getPluginManager().registerEvents(warnListener, this);
        getServer().getPluginManager().registerEvents(banListener, this);

        var counter = new PunishmentCounter();
        PluginMetrics.start(
                this,
                counter,
                url.isBlank() ? "H2" : url.startsWith("jdbc:mysql:") ? "MySQL" : "PostgreSQL",
                url.isBlank() ? "standalone" : "network");
        if (repository instanceof JdbcPunishmentRepository shared) {
            var network =
                    new NetworkListener(this, shared, changes.orElseThrow(), cache, banListener, config, adventure);
            getServer().getPluginManager().registerEvents(network, this);
            getServer().getScheduler().runTaskTimerAsynchronously(this, network::poll, 1, ticks);
        }

        Executor mainThread = task -> getServer().getScheduler().runTask(this, task);
        var support = new CommandSupport(
                repository,
                cache,
                config,
                adventure,
                new ExemptionCheck(OfflinePermissionLookups.detect(getServer(), mainThread), mainThread),
                counter);
        var commandManager = LegacyPaperCommandManager.createNative(this, ExecutionCoordinator.asyncCoordinator());
        new PunishmentCommands(adventure, repository, config).register(commandManager);
        var timedCommands = new TimedPunishmentCommands(support, repository, config);
        timedCommands.register(commandManager, PunishmentType.MUTE, mute -> {});
        timedCommands.register(commandManager, PunishmentType.BAN, banListener::kick);
        new WarnCommands(support, repository, warnListener).register(commandManager);
        new KickCommands(this, support, config).register(commandManager);
        new ImportCommands(
                        getConfig(),
                        getDataFolder().toPath(),
                        mainThread,
                        config,
                        (sender, message) -> adventure.sender(sender).sendMessage(message),
                        punishment -> {
                            if (punishment.type() == PunishmentType.BAN || punishment.type() == PunishmentType.MUTE) {
                                cache.put(punishment);
                            }
                            if (punishment.type() == PunishmentType.BAN) banListener.kick(punishment);
                        })
                .register(commandManager);
        ready = true;
    }

    @Override
    public synchronized void onDisable() {
        stopped = true;
        ready = false;
        getServer().getScheduler().cancelTasks(this);
        if (database != null) {
            var closing = database;
            database = null;
            java.util.concurrent.CompletableFuture.runAsync(() -> closeDatabase(closing));
        }
        if (adventure != null) {
            adventure.close();
            adventure = null;
        }
    }

    private void closeDatabase(AutoCloseable closing) {
        try {
            closing.close();
        } catch (Exception failure) {
            getLogger().warning("Database shutdown failed");
        }
    }
}
