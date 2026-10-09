package com.bradenkennedy.punishment;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.ActivePunishmentCache;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import com.bradenkennedy.punishment.storage.model.PunishmentModel;
import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

public abstract class ServerTestBase {

    protected ServerMock server;
    protected PluginMock plugin;
    protected H2PunishmentRepository repository;
    protected ActivePunishmentCache cache;
    protected BukkitAudiences audiences;
    protected PluginConfig config;

    @BeforeEach
    void setUpServer(@TempDir File dataFolder) throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        repository = new H2PunishmentRepository(dataFolder);
        cache = new ActivePunishmentCache();
        audiences = BukkitAudiences.create(plugin);
        config = new PluginConfig(plugin, MiniMessage.miniMessage());
    }

    @AfterEach
    void tearDownServer() throws Exception {
        audiences.close();
        repository.connectionSource.close();
        MockBukkit.unmock();
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

    protected Punishment store(UUID target, PunishmentType type, Instant expiry) {
        var punishment = new Punishment(
                UUID.randomUUID(),
                target,
                type,
                new PunishmentIssuer(UUID.randomUUID(), Instant.now()),
                "griefing",
                expiry,
                false);
        repository.create(punishment);
        return punishment;
    }
}
