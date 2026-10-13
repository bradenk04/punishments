package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.importer.PunishmentImporter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ImportCommandsTest extends CommandTestBase {

    private static final UUID PERMANENT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void registerCommands(@TempDir Path serverFolder) throws Exception {
        for (String name : new String[] {"banned-players.json", "banned-ips.json"}) {
            try (var in = getClass().getResourceAsStream("/import/vanilla/" + name)) {
                Files.copy(in, serverFolder.resolve(name));
            }
        }
        var importer = new PunishmentImporter(repository, ban -> {}, plugin.getLogger());
        new ImportCommands(
                        audiences,
                        config,
                        support,
                        importer,
                        serverFolder.toFile(),
                        serverFolder.toFile(),
                        plugin.getLogger())
                .register(manager);
    }

    @Test
    void consoleImportStoresBans() {
        run(server.getConsoleSender(), "punish import vanilla");

        assertTrue(repository.findActive(PERMANENT, PunishmentType.BAN).isPresent());
    }

    @Test
    void dryRunStoresNothing() {
        run(server.getConsoleSender(), "punish import vanilla --dry-run");

        assertTrue(history(PERMANENT).isEmpty());
    }

    @Test
    void playersCannotImport() {
        PlayerMock player = server.addPlayer("Staff");
        player.setOp(true);

        var failure = assertThrows(CompletionException.class, () -> run(player, "punish import vanilla"));

        assertInstanceOf(InvalidCommandSenderException.class, failure.getCause());
        assertFalse(repository.findActive(PERMANENT, PunishmentType.BAN).isPresent());
    }

    @Test
    void missingPlayersFileStoresNothing(@TempDir File emptyFolder) {
        var importer = new PunishmentImporter(repository, ban -> {}, plugin.getLogger());
        var commands =
                new ImportCommands(audiences, config, support, importer, emptyFolder, emptyFolder, plugin.getLogger());
        var other = new TestCommandManager();
        commands.register(other);

        other.commandExecutor()
                .executeCommand(server.getConsoleSender(), "punish import vanilla")
                .join();

        assertEquals(0, history(PERMANENT).size());
    }
}
