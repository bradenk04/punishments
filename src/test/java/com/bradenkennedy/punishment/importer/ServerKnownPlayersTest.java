package com.bradenkennedy.punishment.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.ServerTestBase;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ServerKnownPlayersTest extends ServerTestBase {

    @Test
    void snapshotsPlayersByLowerCaseNameOnTheMainThread() throws Exception {
        UUID id = server.addPlayer("StaffMember").getUniqueId();

        CompletableFuture<Map<String, UUID>> snapshot =
                CompletableFuture.supplyAsync(() -> new ServerKnownPlayers(plugin).get());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!snapshot.isDone() && System.nanoTime() < deadline) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }

        assertTrue(snapshot.isDone());
        assertEquals(id, snapshot.get().get("staffmember"));
    }
}
