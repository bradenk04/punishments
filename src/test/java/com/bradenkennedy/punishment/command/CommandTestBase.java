package com.bradenkennedy.punishment.command;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import com.bradenkennedy.punishment.storage.model.PunishmentModel;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

abstract class CommandTestBase {

    protected final TestCommandManager manager = new TestCommandManager();
    protected ServerMock server;
    protected PluginMock plugin;
    protected H2PunishmentRepository repository;
    protected BukkitAudiences audiences;
    protected PluginConfig config;
    protected CommandSupport support;

    @BeforeEach
    void setUpServer(@TempDir File dataFolder) throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        repository = new H2PunishmentRepository(dataFolder);
        audiences = BukkitAudiences.create(plugin);
        config = new PluginConfig(plugin, MiniMessage.miniMessage());
        support = new CommandSupport(repository, config, audiences);
    }

    @AfterEach
    void tearDownServer() throws Exception {
        audiences.close();
        repository.connectionSource.close();
        MockBukkit.unmock();
    }

    protected void run(CommandSender sender, String input) {
        manager.commandExecutor().executeCommand(sender, input).join();
    }

    protected <E extends Event & Cancellable> void cancelEvents(Class<E> type) {
        server.getPluginManager()
                .registerEvent(
                        type,
                        new Listener() {},
                        EventPriority.NORMAL,
                        (listener, event) -> ((Cancellable) event).setCancelled(true),
                        plugin);
    }

    protected List<Punishment> history(UUID player) {
        return repository.findHistory(player);
    }

    protected List<Punishment> history(UUID player, PunishmentType type) {
        return history(player).stream().filter(p -> p.type() == type).toList();
    }

    protected PunishmentModel storedModel(UUID punishmentId) throws SQLException {
        return H2PunishmentRepository.punishmentDao.queryForId(punishmentId);
    }
}
