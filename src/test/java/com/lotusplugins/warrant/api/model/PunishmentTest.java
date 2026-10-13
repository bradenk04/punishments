package com.bradenkennedy.punishment.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentTest {

    private final UUID id = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final PunishmentIssuer issuer = new PunishmentIssuer(UUID.randomUUID(), Instant.now());

    private final Revocation revocation = new Revocation(UUID.randomUUID(), "mistake", Instant.now());

    private Punishment punishment(Instant expiry, boolean revoked) {
        return new Punishment(id, target, PunishmentType.BAN, issuer, "reason", expiry, revoked ? revocation : null);
    }

    @Test
    void permanentPunishmentNeverExpires() {
        assertFalse(punishment(null, false).expired());
    }

    @Test
    void punishmentWithPastExpiryIsExpired() {
        assertTrue(punishment(Instant.now().minus(Duration.ofHours(1)), false).expired());
    }

    @Test
    void punishmentWithFutureExpiryIsNotExpired() {
        assertFalse(punishment(Instant.now().plus(Duration.ofHours(1)), false).expired());
    }

    @Test
    void revokedFlagDoesNotAffectExpiry() {
        assertFalse(punishment(Instant.now().plus(Duration.ofHours(1)), true).expired());
    }

    @Test
    void exposesComponents() {
        Instant expiry = Instant.now().plus(Duration.ofHours(1));
        Punishment punishment = punishment(expiry, true);

        assertEquals(id, punishment.id());
        assertEquals(target, punishment.target());
        assertEquals(PunishmentType.BAN, punishment.type());
        assertEquals(issuer, punishment.issuer());
        assertEquals("reason", punishment.reason());
        assertEquals(expiry, punishment.expiry());
        assertTrue(punishment.revoked());
    }

    @Test
    void equalityFollowsComponents() {
        assertEquals(punishment(null, false), punishment(null, false));
        assertEquals(punishment(null, false).hashCode(), punishment(null, false).hashCode());
        assertNotEquals(punishment(null, false), punishment(null, true));
    }
}
