package com.bradenkennedy.punishment.exemption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class ExemptionCheckTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private final List<String> queried = new CopyOnWriteArrayList<>();

    private ExemptionCheck checkWith(boolean offlineAnswer) {
        return new ExemptionCheck(
                (id, permission) -> {
                    queried.add(id + ":" + permission);
                    return CompletableFuture.completedFuture(offlineAnswer);
                },
                Runnable::run);
    }

    @Test
    void onlinePlayerWithPermissionIsExemptWithoutAskingLookup() {
        assertTrue(checkWith(false).isExempt(PLAYER, Optional.of(true)).join());
        assertTrue(queried.isEmpty());
    }

    @Test
    void onlinePlayerWithoutPermissionIsNotExemptEvenIfLookupWouldSayYes() {
        assertFalse(checkWith(true).isExempt(PLAYER, Optional.of(false)).join());
        assertTrue(queried.isEmpty());
    }

    @Test
    void offlinePlayerUsesLookupForExemptPermission() {
        assertTrue(checkWith(true).isExempt(PLAYER, Optional.empty()).join());
        assertEquals(List.of(PLAYER + ":punishments.exempt"), queried);
    }

    @Test
    void offlinePlayerWithoutPermissionIsNotExempt() {
        assertFalse(checkWith(false).isExempt(PLAYER, Optional.empty()).join());
    }

    @Test
    void offlinePlayerIsNotExemptWhenNoLookupAvailable() {
        var check = new ExemptionCheck(OfflinePermissionLookup.NONE, Runnable::run);
        assertFalse(check.isExempt(PLAYER, Optional.empty()).join());
    }

    @Test
    void lookupFailureSurfacesInsteadOfAllowingPunishment() {
        var check = new ExemptionCheck(
                (id, permission) -> CompletableFuture.failedFuture(new IllegalStateException("backend down")),
                Runnable::run);
        var future = check.isExempt(PLAYER, Optional.empty());
        var thrown = assertThrows(CompletionException.class, future::join);
        assertTrue(thrown.getCause() instanceof IllegalStateException);
    }
}
