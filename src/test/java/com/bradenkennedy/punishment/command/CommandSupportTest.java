package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.events.PlayerPunishedEvent;
import com.bradenkennedy.punishment.api.events.PlayerPunishmentRevokedEvent;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.context.CommandContext;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CommandSupportTest extends CommandTestBase {

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
        player.addAttachment(plugin, "punishments.exempt", true);

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

    private Punishment punishment(UUID targetId) {
        return new Punishment(
                UUID.randomUUID(),
                targetId,
                PunishmentType.WARN,
                new PunishmentIssuer(UUID.randomUUID(), Instant.now()),
                "reason",
                null,
                false);
    }

    @Test
    void tryPunishStoresPunishmentAndReturnsTrue() {
        PlayerMock target = server.addPlayer();
        Punishment punishment = punishment(target.getUniqueId());

        assertTrue(support.tryPunish(context(server.getConsoleSender()), target, punishment));

        assertEquals(1, history(target.getUniqueId()).size());
    }

    @Test
    void tryPunishReturnsFalseAndStoresNothingWhenCancelled() {
        PlayerMock target = server.addPlayer();
        cancelEvents(PlayerPunishedEvent.class);

        assertFalse(support.tryPunish(context(server.getConsoleSender()), target, punishment(target.getUniqueId())));

        assertTrue(history(target.getUniqueId()).isEmpty());
    }

    @Test
    void tryRevokeRevokesPunishmentWithReasonAndReturnsTrue() throws Exception {
        PlayerMock target = server.addPlayer();
        Punishment punishment = punishment(target.getUniqueId());
        repository.create(punishment);
        var ctx = context(server.getConsoleSender());
        ctx.store("reason", "mistake");

        assertTrue(support.tryRevoke(ctx, target, punishment));

        var model = storedModel(punishment.id());
        assertTrue(model.isRevoked());
        assertEquals("mistake", model.getRevokedReason());
        assertEquals(new UUID(0, 0), model.getRevokedBy());
    }

    @Test
    void tryRevokeReturnsFalseAndKeepsPunishmentWhenCancelled() {
        PlayerMock target = server.addPlayer();
        Punishment punishment = punishment(target.getUniqueId());
        repository.create(punishment);
        cancelEvents(PlayerPunishmentRevokedEvent.class);

        assertFalse(support.tryRevoke(context(server.getConsoleSender()), target, punishment));

        assertFalse(history(target.getUniqueId()).getFirst().revoked());
    }
}
