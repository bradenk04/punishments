package com.bradenkennedy.punishment.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EssentialsMuteReaderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static File fixture() throws URISyntaxException {
        return new File(EssentialsMuteReaderTest.class
                .getResource("/import/essentials/userdata")
                .toURI());
    }

    private static ImportBatch read() throws Exception {
        return EssentialsMuteReader.read(fixture(), NOW, Logger.getAnonymousLogger());
    }

    private static Punishment byTarget(ImportBatch batch, String prefix) {
        return batch.records().stream()
                .map(ImportRecord::punishment)
                .filter(p -> p.target().toString().startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void countsMutesJailsAndUnreadableFiles() throws Exception {
        ImportBatch batch = read();

        assertEquals(2, batch.records().size());
        assertEquals(1, batch.skipped());
        assertEquals(2, batch.failed());
    }

    @Test
    void timedMuteKeepsReasonAndExpiry() throws Exception {
        Punishment mute = byTarget(read(), "e1e1e1e1");

        assertEquals(PunishmentType.MUTE, mute.type());
        assertEquals("spam", mute.reason());
        assertEquals(Instant.ofEpochMilli(4102444800000L), mute.expiry());
        assertEquals(VanillaBanParser.CONSOLE_ID, mute.issuer().issuer());
        assertEquals(NOW, mute.issuer().issuedAt());
        assertFalse(mute.revoked());
    }

    @Test
    void zeroTimeoutIsPermanent() throws Exception {
        Punishment mute = byTarget(read(), "e2e2e2e2");

        assertNull(mute.expiry());
        assertNull(mute.reason());
    }

    @Test
    void sourceIdCombinesPlayerAndTimeout() throws Exception {
        assertEquals(
                "e2e2e2e2-2222-2222-2222-222222222222@0",
                read().records().stream()
                        .map(ImportRecord::sourceId)
                        .filter(id -> id.startsWith("e2e2"))
                        .findFirst()
                        .orElseThrow());
    }

    @Test
    void missingUserdataFolderIsAnError(@TempDir File folder) {
        assertThrows(
                IOException.class,
                () -> EssentialsMuteReader.read(new File(folder, "absent"), NOW, Logger.getAnonymousLogger()));
    }
}
