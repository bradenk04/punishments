package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.importer.ImportSources;
import com.bradenkennedy.punishment.importer.LegacyH2;
import com.bradenkennedy.punishment.importer.PunishmentImporter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ImportCommandsTest extends CommandTestBase {

    private static final UUID PERMANENT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID ESSENTIALS_MUTED = UUID.fromString("e1e1e1e1-1111-1111-1111-111111111111");

    private Path serverFolder;

    @BeforeEach
    void registerCommands(@TempDir Path serverFolder) throws Exception {
        this.serverFolder = serverFolder;
        for (String name : new String[] {"banned-players.json", "banned-ips.json"}) {
            try (var in = getClass().getResourceAsStream("/import/vanilla/" + name)) {
                Files.copy(in, serverFolder.resolve(name));
            }
        }
        Path userdata = Files.createDirectories(serverFolder.resolve("Essentials/userdata"));
        for (String name : new String[] {"e1e1e1e1-1111-1111-1111-111111111111.yml"}) {
            try (var in = getClass().getResourceAsStream("/import/essentials/userdata/" + name)) {
                Files.copy(in, userdata.resolve(name));
            }
        }
        register(manager, serverFolder.toFile());
    }

    private void register(TestCommandManager target, File folder) {
        var importer = new PunishmentImporter(repository, ban -> {}, plugin.getLogger());
        var sources = new ImportSources(
                folder,
                folder,
                new LegacyH2(folder.toPath(), LegacyH2.MAVEN_CENTRAL, LegacyH2.SHA256),
                Map::of,
                plugin.getLogger());
        new ImportCommands(audiences, config, support, importer, sources, plugin.getLogger()).register(target);
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
    void essentialsImportStoresMutesAndVanillaBans() {
        run(server.getConsoleSender(), "punish import essentials");

        assertTrue(repository.findActive(ESSENTIALS_MUTED, PunishmentType.MUTE).isPresent());
        assertTrue(repository.findActive(PERMANENT, PunishmentType.BAN).isPresent());
    }

    @Test
    void essentialsImportCanRunTwiceWithoutDuplicates() {
        run(server.getConsoleSender(), "punish import essentials");
        run(server.getConsoleSender(), "punish import essentials");

        assertEquals(1, history(ESSENTIALS_MUTED).size());
        assertEquals(1, history(PERMANENT).size());
    }

    @Test
    void playersCannotImport() {
        PlayerMock player = server.addPlayer("Staff");
        player.setOp(true);

        run(player, "punish import vanilla");

        assertFalse(repository.findActive(PERMANENT, PunishmentType.BAN).isPresent());
    }

    @Test
    void missingPlayersFileStoresNothing(@TempDir File emptyFolder) {
        var other = new TestCommandManager();
        register(other, emptyFolder);

        other.commandExecutor()
                .executeCommand(server.getConsoleSender(), "punish import vanilla")
                .join();

        assertEquals(0, history(PERMANENT).size());
    }
}
