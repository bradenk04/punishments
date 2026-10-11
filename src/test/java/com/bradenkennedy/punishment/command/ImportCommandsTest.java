package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportCommandsTest extends CommandTestBase {
    @TempDir
    Path directory;

    private final List<Component> sentMessages = new ArrayList<>();

    private void register() {
        new ImportCommands(
                        plugin.getConfig(),
                        directory,
                        Runnable::run,
                        config,
                        (sender, message) -> sentMessages.add(message),
                        punishment -> {})
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
}
