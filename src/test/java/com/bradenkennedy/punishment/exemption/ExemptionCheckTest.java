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
        return new ExemptionCheck((id, permission) -> {
            queried.add(id + ":" + permission);
            return CompletableFuture.completedFuture(offlineAnswer);
        });
    }

    @Test
    void operatorIsExemptWithoutAskingLookup() {
        assertTrue(checkWith(false).isExempt(PLAYER, true, Optional.empty()).join());
        assertTrue(queried.isEmpty());
    }

    @Test
    void onlinePlayerWithPermissionIsExemptWithoutAskingLookup() {
        assertTrue(checkWith(false).isExempt(PLAYER, false, Optional.of(true)).join());
        assertTrue(queried.isEmpty());
    }

    @Test
    void onlinePlayerWithoutPermissionIsNotExemptEvenIfLookupWouldSayYes() {
        assertFalse(checkWith(true).isExempt(PLAYER, false, Optional.of(false)).join());
        assertTrue(queried.isEmpty());
    }

    @Test
    void offlinePlayerUsesLookupForExemptPermission() {
        assertTrue(checkWith(true).isExempt(PLAYER, false, Optional.empty()).join());
        assertEquals(List.of(PLAYER + ":punishments.exempt"), queried);
    }

    @Test
    void offlinePlayerWithoutPermissionIsNotExempt() {
        assertFalse(checkWith(false).isExempt(PLAYER, false, Optional.empty()).join());
    }

    @Test
    void offlinePlayerIsNotExemptWhenNoLookupAvailable() {
        var check = new ExemptionCheck(OfflinePermissionLookup.NONE);
        assertFalse(check.isExempt(PLAYER, false, Optional.empty()).join());
    }

    @Test
    void operatorIsStillExemptWhenNoLookupAvailable() {
        var check = new ExemptionCheck(OfflinePermissionLookup.NONE);
        assertTrue(check.isExempt(PLAYER, true, Optional.empty()).join());
    }

    @Test
    void lookupFailureSurfacesInsteadOfAllowingPunishment() {
        var check = new ExemptionCheck(
                (id, permission) -> CompletableFuture.failedFuture(new IllegalStateException("backend down")));
        var future = check.isExempt(PLAYER, false, Optional.empty());
        var thrown = assertThrows(CompletionException.class, future::join);
        assertTrue(thrown.getCause() instanceof IllegalStateException);
    }
}
