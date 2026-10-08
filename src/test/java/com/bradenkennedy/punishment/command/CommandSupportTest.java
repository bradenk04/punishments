package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.context.CommandContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CommandSupportTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CommandContext<CommandSender> context(CommandSender sender) {
        return new CommandContext<>(sender, new TestCommandManager());
    }

    @Test
    void nameOfReturnsPlayerName() {
        PlayerMock player = server.addPlayer("Steve");

        assertEquals("Steve", CommandSupport.nameOf(player));
    }

    @Test
    void nameOfFallsBackToUuidForUnnamedOfflinePlayer() {
        UUID uuid = UUID.randomUUID();
        PlayerMock unnamed = new PlayerMock(server, "unnamed", uuid) {
            @Override
            public String getName() {
                return null;
            }
        };

        assertEquals(uuid.toString(), CommandSupport.nameOf(unnamed));
    }

    @Test
    void operatorIsExempt() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertTrue(CommandSupport.isExempt(player));
    }

    @Test
    void playerWithExemptPermissionIsExempt() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "punishments.exempt", true);

        assertTrue(CommandSupport.isExempt(player));
    }

    @Test
    void ordinaryPlayerIsNotExempt() {
        assertFalse(CommandSupport.isExempt(server.addPlayer()));
    }

    @Test
    void offlinePlayerWithoutOpIsNotExempt() {
        assertFalse(CommandSupport.isExempt(server.getOfflinePlayer(UUID.randomUUID())));
    }

    @Test
    void issuerIdIsPlayerUuidForPlayers() {
        PlayerMock player = server.addPlayer();

        assertEquals(player.getUniqueId(), CommandSupport.issuerId(player));
    }

    @Test
    void issuerIdIsZeroUuidForConsole() {
        assertEquals(new UUID(0, 0), CommandSupport.issuerId(server.getConsoleSender()));
    }

    @Test
    void reasonOfReturnsStoredReason() {
        var ctx = context(server.getConsoleSender());
        ctx.store("reason", "griefing");

        assertEquals("griefing", CommandSupport.reasonOf(ctx));
    }

    @Test
    void reasonOfIsNullWhenAbsent() {
        assertNull(CommandSupport.reasonOf(context(server.getConsoleSender())));
    }
}
