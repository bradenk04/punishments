package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.*;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportCommandsTest extends CommandTestBase {
    @TempDir
    Path directory;

    private final List<Component> sentMessages = new ArrayList<>();
    private final List<Punishment> applied = new ArrayList<>();

    private void register() {
        new ImportCommands(
                        plugin.getConfig(),
                        directory,
                        Runnable::run,
                        config,
                        (sender, message) -> sentMessages.add(message),
                        applied::add)
                .register(manager);
    }

    @Test
    void rejectsPlayersWithoutOpeningTheDestination() {
        register();
        var player = server.addPlayer();
        run(player, "punish import vanilla");
        assertEquals(config.message("imports.console-only"), sentMessages.getFirst());
        assertFalse(Files.exists(directory.resolve("punishments.mv.db")));
    }

    @Test
    void rejectsUnknownSourcesWithoutOpeningTheDestination() {
        register();
        run(server.getConsoleSender(), "punish import unknown");
        assertEquals(config.message("imports.unknown-source"), sentMessages.getFirst());
        assertFalse(Files.exists(directory.resolve("punishments.mv.db")));
    }

    @Test
    void declaresImportPermissionAndDryRunFlag() {
        register();
        var command = manager.commands().iterator().next();
        assertEquals("punishments.import", command.commandPermission().permissionString());
        assertTrue(command.flagParser().flags().stream()
                .anyMatch(flag -> flag.name().equals("dry-run")));
    }

    private void prepareDestination() throws Exception {
        repository.connectionSource.close();
        repository = new H2PunishmentRepository(directory.toFile());
        plugin.getConfig().set("imports.vanilla.directory", directory.toString());
    }

    private void writeFixture() throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/imports/banned-players.json"))) {
            Files.write(directory.resolve("banned-players.json"), input.readAllBytes());
        }
    }

    @Test
    void consolePreviewImportAndRerunPreserveHistoryAndEnforcement() throws Exception {
        prepareDestination();
        writeFixture();
        register();
        var target = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        run(server.getConsoleSender(), "punish import vanilla --dry-run");
        assertTrue(repository.findHistory(target).isEmpty());
        assertTrue(applied.isEmpty());
        assertEquals(1, sentMessages.size());
        run(server.getConsoleSender(), "punish import vanilla");
        assertEquals(1, repository.findHistory(target).size());
        assertEquals(1, applied.size());
        run(server.getConsoleSender(), "punish import vanilla");
        assertEquals(1, repository.findHistory(target).size());
        assertEquals(1, applied.size());
        assertEquals(3, sentMessages.size());
    }

    @Test
    void sourceFailureReportsErrorAndReleasesTheImportForRetry() throws Exception {
        prepareDestination();
        Files.writeString(directory.resolve("banned-players.json"), "not valid json");
        register();
        run(server.getConsoleSender(), "punish import vanilla");
        assertEquals(config.message("imports.failed"), sentMessages.getFirst());
        assertTrue(applied.isEmpty());
        writeFixture();
        run(server.getConsoleSender(), "punish import vanilla");
        assertEquals(1, applied.size());
        assertEquals(2, sentMessages.size());
    }
}
