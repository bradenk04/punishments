package com.bradenkennedy.punishment.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentIssuerTest {

    private final UUID uuid = UUID.randomUUID();
    private final Instant issuedAt = Instant.now();

    @Test
    void exposesComponents() {
        PunishmentIssuer issuer = new PunishmentIssuer(uuid, issuedAt);

        assertEquals(uuid, issuer.issuer());
        assertEquals(issuedAt, issuer.issuedAt());
    }

    @Test
    void equalityFollowsComponents() {
        PunishmentIssuer issuer = new PunishmentIssuer(uuid, issuedAt);

        assertEquals(issuer, new PunishmentIssuer(uuid, issuedAt));
        assertEquals(issuer.hashCode(), new PunishmentIssuer(uuid, issuedAt).hashCode());
        assertNotEquals(issuer, new PunishmentIssuer(UUID.randomUUID(), issuedAt));
        assertNotEquals(issuer, new PunishmentIssuer(uuid, issuedAt.plusSeconds(1)));
    }
}
