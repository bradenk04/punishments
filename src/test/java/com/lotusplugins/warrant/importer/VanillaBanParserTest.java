package com.lotusplugins.warrant.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentType;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class VanillaBanParserTest {

    private static Reader fixture(String name) {
        return new InputStreamReader(
                VanillaBanParserTest.class.getResourceAsStream("/import/vanilla/" + name), StandardCharsets.UTF_8);
    }

    @Test
    void parsesPermanentBanWithConsoleIssuer() throws Exception {
        try (Reader reader = fixture("banned-players.json")) {
            Punishment ban = VanillaBanParser.parsePlayers(reader, Logger.getAnonymousLogger())
                    .records()
                    .getFirst()
                    .punishment();

            assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), ban.target());
            assertEquals(PunishmentType.BAN, ban.type());
            assertEquals(VanillaBanParser.CONSOLE_ID, ban.issuer().issuer());
            assertEquals(Instant.parse("2024-01-02T03:04:05Z"), ban.issuer().issuedAt());
            assertEquals("Griefing", ban.reason());
            assertNull(ban.expiry());
        }
    }

    @Test
    void keepsExpiredBansAsHistory() throws Exception {
        try (Reader reader = fixture("banned-players.json")) {
            Punishment ban = VanillaBanParser.parsePlayers(reader, Logger.getAnonymousLogger())
                    .records()
                    .get(1)
                    .punishment();

            assertEquals(Instant.parse("2020-01-02T00:00:00Z"), ban.expiry());
        }
    }

    @Test
    void countsMalformedEntriesAsFailed() throws Exception {
        try (Reader reader = fixture("banned-players.json")) {
            ImportBatch batch = VanillaBanParser.parsePlayers(reader, Logger.getAnonymousLogger());

            assertEquals(2, batch.records().size());
            assertEquals(1, batch.failed());
        }
    }

    @Test
    void countsIpBansAsSkipped() throws Exception {
        try (Reader reader = fixture("banned-ips.json")) {
            ImportBatch batch = VanillaBanParser.parseIps(reader);

            assertEquals(0, batch.records().size());
            assertEquals(1, batch.skipped());
        }
    }
}
