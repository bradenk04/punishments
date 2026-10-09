package com.bradenkennedy.punishment;

import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.command.CommandSupport;
import com.bradenkennedy.punishment.command.KickCommands;
import com.bradenkennedy.punishment.command.PunishmentCommands;
import com.bradenkennedy.punishment.command.TimedPunishmentCommands;
import com.bradenkennedy.punishment.command.WarnCommands;
import com.bradenkennedy.punishment.exemption.ExemptionCheck;
import com.bradenkennedy.punishment.exemption.OfflinePermissionLookups;
import com.bradenkennedy.punishment.listener.BanListener;
import com.bradenkennedy.punishment.listener.CacheLoadListener;
import com.bradenkennedy.punishment.listener.MuteListener;
import com.bradenkennedy.punishment.listener.WarnListener;
import com.bradenkennedy.punishment.storage.ActivePunishmentCache;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
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
        getServer().getPluginManager().registerEvents(new CacheLoadListener(repository, cache), this);
        getServer()
                .getPluginManager()
                .registerEvents(new MuteListener(cache, config.blockedMuteCommands(), config, adventure), this);
        getServer().getPluginManager().registerEvents(warnListener, this);
        getServer().getPluginManager().registerEvents(banListener, this);

        Executor mainThread = task -> getServer().getScheduler().runTask(this, task);
        var support = new CommandSupport(
                repository,
                cache,
                config,
                adventure,
                new ExemptionCheck(OfflinePermissionLookups.detect(getServer(), mainThread), mainThread));
        var commandManager = LegacyPaperCommandManager.createNative(this, ExecutionCoordinator.asyncCoordinator());
        new PunishmentCommands(adventure, repository, config).register(commandManager);
        var timedCommands = new TimedPunishmentCommands(support, repository, config);
        timedCommands.register(commandManager, PunishmentType.MUTE, mute -> {});
        timedCommands.register(commandManager, PunishmentType.BAN, banListener::kick);
        new WarnCommands(support, repository, warnListener).register(commandManager);
        new KickCommands(this, support, config).register(commandManager);
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
    }
}
