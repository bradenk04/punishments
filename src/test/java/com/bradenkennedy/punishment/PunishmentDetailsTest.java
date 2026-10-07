package com.bradenkennedy.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

class PunishmentDetailsTest {
    private final Punishment punishment = new Punishment(UUID.fromString("12345678-1234-1234-1234-123456789abc"),
            UUID.randomUUID(), PunishmentType.BAN,
            new PunishmentIssuer(new UUID(0, 0), Instant.parse("2026-01-02T03:04:05Z")), null, null, false);

    @Test void suppliesAllAppealReferencesAndConsoleIssuer() {
        assertEquals(punishment.id() + " Console 2026-01-02T03:04:05Z",
                LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage()
                        .deserialize("<id> <staff> <date>", PunishmentDetails.resolvers(punishment))));
    }

    @Test void staffNameCannotInjectMiniMessageTags() {
        assertEquals("<red>staff", LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage()
                .deserialize("<staff>", PunishmentDetails.resolvers(punishment, "<red>staff"))));
    }
}
