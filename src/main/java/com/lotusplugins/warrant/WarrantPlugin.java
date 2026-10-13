package com.lotusplugins.warrant;

import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.command.CommandSupport;
import com.lotusplugins.warrant.command.ImportCommands;
import com.lotusplugins.warrant.command.KickCommands;
import com.lotusplugins.warrant.command.PunishmentCommands;
import com.lotusplugins.warrant.command.TimedPunishmentCommands;
import com.lotusplugins.warrant.command.WarnCommands;
import com.lotusplugins.warrant.exemption.ExemptionCheck;
import com.lotusplugins.warrant.exemption.OfflinePermissionLookups;
import com.lotusplugins.warrant.importer.ImportEnforcement;
import com.lotusplugins.warrant.importer.ImportSources;
import com.lotusplugins.warrant.importer.LegacyH2;
import com.lotusplugins.warrant.importer.PunishmentImporter;
import com.lotusplugins.warrant.importer.ServerKnownPlayers;
import com.lotusplugins.warrant.listener.BanListener;
import com.lotusplugins.warrant.listener.CacheLoadListener;
import com.lotusplugins.warrant.listener.MuteListener;
import com.lotusplugins.warrant.listener.WarnListener;
import com.lotusplugins.warrant.metrics.PluginMetrics;
import com.lotusplugins.warrant.metrics.PunishmentCounter;
import com.lotusplugins.warrant.storage.ActivePunishmentCache;
import com.lotusplugins.warrant.storage.H2PunishmentRepository;
import com.lotusplugins.warrant.storage.PunishmentRepository;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

public class WarrantPlugin extends JavaPlugin {

    private BukkitAudiences adventure;

    @Override
    public void onEnable() {
        this.adventure = BukkitAudiences.create(this);

        PunishmentRepository repository;
        try {
            repository = new H2PunishmentRepository(this.getDataFolder());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        var config = new PluginConfig(this, MiniMessage.miniMessage());
        var cache = new ActivePunishmentCache();
        var banListener = new BanListener(this, repository, config);
        var warnListener = new WarnListener(repository, config, adventure);
        getServer().getPluginManager().registerEvents(new CacheLoadListener(this, repository, cache, config), this);
        getServer()
                .getPluginManager()
                .registerEvents(new MuteListener(cache, config.blockedMuteCommands(), config, adventure), this);
        getServer().getPluginManager().registerEvents(warnListener, this);
        getServer().getPluginManager().registerEvents(banListener, this);

        var counter = new PunishmentCounter();
        PluginMetrics.start(this, counter, "H2", "standalone");

        Executor mainThread = task -> getServer().getScheduler().runTask(this, task);
        var support = new CommandSupport(
                repository,
                cache,
                config,
                adventure,
                new ExemptionCheck(OfflinePermissionLookups.detect(getServer(), mainThread), mainThread),
                counter,
                getLogger());
        var commandManager = LegacyPaperCommandManager.createNative(this, ExecutionCoordinator.asyncCoordinator());
        new PunishmentCommands(adventure, repository, config, support).register(commandManager);
        var timedCommands = new TimedPunishmentCommands(support, repository, config);
        timedCommands.register(commandManager, PunishmentType.MUTE, mute -> {});
        timedCommands.register(commandManager, PunishmentType.BAN, banListener::kick);
        new WarnCommands(support, repository, warnListener).register(commandManager);
        new KickCommands(this, support, config).register(commandManager);
        var importer =
                new PunishmentImporter(repository, new ImportEnforcement(this, cache, banListener::kick), getLogger());
        var importSources = new ImportSources(
                getServer().getWorldContainer(),
                getDataFolder().getParentFile(),
                new LegacyH2(getDataFolder().toPath().resolve("lib"), LegacyH2.MAVEN_CENTRAL, LegacyH2.SHA256),
                new ServerKnownPlayers(this),
                getLogger());
        new ImportCommands(adventure, config, support, importer, importSources, getLogger()).register(commandManager);
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
    }
}
