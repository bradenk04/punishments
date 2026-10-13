package com.bradenkennedy.punishment.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PunishmentImporterTest {

    private static final UUID PERMANENT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final List<Punishment> enforced = new ArrayList<>();
    private H2PunishmentRepository repository;
    private PunishmentImporter importer;
    private ImportBatch batch;

    @BeforeEach
    void setUp(@TempDir File dataFolder) throws Exception {
        repository = new H2PunishmentRepository(dataFolder);
        importer = new PunishmentImporter(repository, enforced::add, Logger.getAnonymousLogger());
        try (Reader reader = new InputStreamReader(
                        getClass().getResourceAsStream("/import/vanilla/banned-players.json"), StandardCharsets.UTF_8);
                Reader ips = new InputStreamReader(
                        getClass().getResourceAsStream("/import/vanilla/banned-ips.json"), StandardCharsets.UTF_8)) {
            batch = VanillaBanParser.parsePlayers(reader).plus(VanillaBanParser.parseIps(ips));
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.connectionSource.close();
    }

    private ImportReport run(boolean dryRun) {
        return importer.run(ImportSource.VANILLA, batch, dryRun, processed -> {});
    }

    @Test
    void importsRecordsAndReportsSkippedAndFailed() {
        assertEquals(new ImportReport(2, 1, 1), run(false));
        assertEquals(2, enforced.size());
    }

    @Test
    void activeBanIsEnforcedAfterImport() {
        run(false);

        assertTrue(repository.findActive(PERMANENT, PunishmentType.BAN).isPresent());
    }

    @Test
    void rerunDoesNotDuplicate() {
        run(false);
        enforced.clear();

        assertEquals(new ImportReport(0, 3, 1), run(false));
        assertEquals(1, repository.findHistory(PERMANENT).size());
        assertTrue(enforced.isEmpty());
    }

    @Test
    void dryRunReportsCountsWithoutWriting() {
        assertEquals(new ImportReport(2, 1, 1), run(true));
        assertTrue(repository.findHistory(PERMANENT).isEmpty());
        assertTrue(enforced.isEmpty());
    }

    @Test
    void dryRunAfterImportReportsDuplicatesAsSkipped() {
        run(false);

        assertEquals(new ImportReport(0, 3, 1), run(true));
    }

    @Test
    void reportsProgressEveryInterval() {
        List<Integer> reported = new ArrayList<>();
        List<ImportRecord> many = new ArrayList<>();
        for (int i = 0; i < PunishmentImporter.PROGRESS_INTERVAL * 2; i++) {
            many.add(new ImportRecord("row-" + i, batch.records().getFirst().punishment()));
        }

        importer.run(ImportSource.VANILLA, new ImportBatch(many, 0, 0), true, reported::add);

        assertEquals(List.of(PunishmentImporter.PROGRESS_INTERVAL, PunishmentImporter.PROGRESS_INTERVAL * 2), reported);
    }
}
