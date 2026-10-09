package com.bradenkennedy.punishment.storage.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PunishmentModelTest {

    private static final Instant ISSUED_AT = Instant.ofEpochMilli(1_700_000_000_000L);
    private static final Instant EXPIRY = ISSUED_AT.plusSeconds(3600);

    private final UUID id = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final UUID issuer = UUID.randomUUID();

    private Punishment punishment(PunishmentType type, boolean revoked) {
        return new Punishment(id, target, type, new PunishmentIssuer(issuer, ISSUED_AT), "reason", EXPIRY, revoked);
    }

    @ParameterizedTest
    @EnumSource(PunishmentType.class)
    void punishmentRoundTripsForEveryType(PunishmentType type) {
        Punishment original = punishment(type, false);

        assertEquals(original, new PunishmentModel(original).toPunishment());
    }

    @Test
    void revokedFlagSurvivesRoundTrip() {
        Punishment original = punishment(PunishmentType.values()[0], true);

        assertTrue(new PunishmentModel(original).toPunishment().revoked());
    }

    @Test
    void punishmentConstructorCopiesFieldsAndLeavesRevocationDetailsEmpty() {
        PunishmentType type = PunishmentType.values()[0];

        PunishmentModel model = new PunishmentModel(punishment(type, true));

        assertEquals(id, model.getUuid());
        assertEquals(target, model.getTarget());
        assertEquals(type, model.getType());
        assertEquals(issuer, model.getIssuerUuid());
        assertEquals(ISSUED_AT, model.getIssuedAt());
        assertEquals("reason", model.getReason());
        assertEquals(EXPIRY, model.getExpiry());
        assertTrue(model.isRevoked());
        assertNull(model.getRevokedReason());
        assertNull(model.getRevokedBy());
    }

    @Test
    void fullConstructorSetsEveryField() {
        UUID revoker = UUID.randomUUID();
        PunishmentType type = PunishmentType.values()[0];

        PunishmentModel model = new PunishmentModel(
                id,
                type,
                new PunishmentIssuer(issuer, ISSUED_AT),
                "reason",
                EXPIRY,
                true,
                "mistake",
                revoker,
                ISSUED_AT.plusSeconds(10));

        assertEquals(id, model.getUuid());
        assertEquals(type, model.getType());
        assertEquals(issuer, model.getIssuerUuid());
        assertEquals(ISSUED_AT, model.getIssuedAt());
        assertEquals("reason", model.getReason());
        assertEquals(EXPIRY, model.getExpiry());
        assertTrue(model.isRevoked());
        assertEquals("mistake", model.getRevokedReason());
        assertEquals(revoker, model.getRevokedBy());
    }

    @Test
    void noArgConstructorIsEmpty() {
        PunishmentModel model = new PunishmentModel();

        assertNull(model.getUuid());
        assertNull(model.getTarget());
        assertNull(model.getType());
        assertNull(model.getIssuedAt());
        assertNull(model.getExpiry());
        assertFalse(model.isRevoked());
    }

    @Test
    void settersUpdateMatchingGetters() {
        UUID revoker = UUID.randomUUID();
        PunishmentType type = PunishmentType.values()[0];
        PunishmentModel model = new PunishmentModel();

        model.setUuid(id);
        model.setTarget(target);
        model.setType(type);
        model.setIssuerUuid(issuer);
        model.setIssuedAt(ISSUED_AT);
        model.setReason("reason");
        model.setExpiry(EXPIRY);
        model.setIsRevoked(true);
        model.setRevokedReason("mistake");
        model.setRevokedBy(revoker);
        model.setRevokedAt(ISSUED_AT);

        assertEquals(id, model.getUuid());
        assertEquals(target, model.getTarget());
        assertEquals(type, model.getType());
        assertEquals(issuer, model.getIssuerUuid());
        assertEquals(ISSUED_AT, model.getIssuedAt());
        assertEquals("reason", model.getReason());
        assertEquals(EXPIRY, model.getExpiry());
        assertTrue(model.isRevoked());
        assertEquals("mistake", model.getRevokedReason());
        assertEquals(revoker, model.getRevokedBy());
    }
}
