package com.bradenkennedy.punishment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bradenkennedy.punishment.api.model.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentMessagesTest {
    @Test
    void reasonTokensAndReplacementCharactersRemainLiteral() {
        var punishment = new Punishment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PunishmentType.BAN,
                new PunishmentIssuer(new UUID(0, 0), Instant.EPOCH),
                "<staff> $1 \\ reason",
                null,
                null);
        assertEquals(
                "Console: <staff> $1 \\ reason | Permanent",
                PunishmentMessages.render("<staff>: <reason> | <expiry>", punishment));
    }
}
