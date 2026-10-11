package com.bradenkennedy.punishment.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.ServerTestBase;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@SuppressWarnings("deprecation")
class MuteListenerTest extends ServerTestBase {

    private MuteListener listener;
    private PlayerMock player;

    @BeforeEach
    void createListener() {
        listener = new MuteListener(cache, List.of("msg", "Tell"), config, audiences);
        player = server.addPlayer("Target");
    }

    @Override
    protected Punishment store(UUID target, PunishmentType type, Instant expiry) {
        var punishment = super.store(target, type, expiry);
        cache.put(punishment);
        return punishment;
    }

    private AsyncPlayerChatEvent chat() {
        return new AsyncPlayerChatEvent(false, player, "hello", Set.of(player));
    }

    private PlayerCommandPreprocessEvent command(String message) {
        return new PlayerCommandPreprocessEvent(player, message);
    }

    @Test
    void mutedPlayerChatIsCancelled() {
        store(player.getUniqueId(), PunishmentType.MUTE, null);
        var event = chat();

        listener.onChat(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void temporarilyMutedPlayerChatIsCancelled() {
        store(player.getUniqueId(), PunishmentType.MUTE, Instant.now().plus(Duration.ofHours(1)));
        var event = chat();

        listener.onChat(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void unmutedPlayerChatIsAllowed() {
        var event = chat();

        listener.onChat(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void expiredMuteChatIsAllowed() {
        store(player.getUniqueId(), PunishmentType.MUTE, Instant.now().minus(Duration.ofHours(1)));
        var event = chat();

        listener.onChat(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void revokedMuteChatIsAllowed() {
        var mute = store(player.getUniqueId(), PunishmentType.MUTE, null);
        repository.revoke(mute.id(), player.getUniqueId(), "appeal", Instant.now());
        cache.remove(mute.target(), mute.type());
        var event = chat();

        listener.onChat(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void banDoesNotMuteChat() {
        store(player.getUniqueId(), PunishmentType.BAN, null);
        var event = chat();

        listener.onChat(event);

        assertFalse(event.isCancelled());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/msg Friend hi", "/MSG Friend hi", "/tell Friend hi", "/minecraft:msg Friend hi", "/msg"})
    void mutedPlayerBlockedCommandIsCancelled(String message) {
        store(player.getUniqueId(), PunishmentType.MUTE, null);
        var event = command(message);

        listener.onCommand(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void mutedPlayerUnblockedCommandIsAllowed() {
        store(player.getUniqueId(), PunishmentType.MUTE, null);
        var event = command("/spawn");

        listener.onCommand(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void unmutedPlayerBlockedCommandIsAllowed() {
        var event = command("/msg Friend hi");

        listener.onCommand(event);

        assertFalse(event.isCancelled());
    }
}
