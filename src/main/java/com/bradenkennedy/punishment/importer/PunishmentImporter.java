package com.bradenkennedy.punishment.importer;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import com.bradenkennedy.punishment.storage.StorageException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PunishmentImporter {

    public static final int PROGRESS_INTERVAL = 1000;

    private final PunishmentRepository repository;
    private final Consumer<Punishment> onImported;
    private final Logger logger;

    public PunishmentImporter(PunishmentRepository repository, Consumer<Punishment> onImported, Logger logger) {
        this.repository = repository;
        this.onImported = onImported;
        this.logger = logger;
    }

    public ImportReport run(ImportSource source, ImportBatch batch, boolean dryRun, IntConsumer progress) {
        String sourceName = source.name();
        int imported = 0;
        int skipped = batch.skipped();
        int failed = batch.failed();
        int processed = 0;
        for (ImportRecord record : batch.records()) {
            try {
                if (!dryRun && repository.createImported(record.punishment(), sourceName, record.sourceId())) {
                    imported++;
                    onImported.accept(record.punishment());
                } else if (dryRun && !repository.hasImported(sourceName, record.sourceId())) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (StorageException e) {
                logger.log(Level.SEVERE, "Could not import " + sourceName + " row " + record.sourceId(), e);
                failed++;
            }
            if (++processed % PROGRESS_INTERVAL == 0) {
                progress.accept(processed);
            }
        }
        return new ImportReport(imported, skipped, failed);
    }
}
