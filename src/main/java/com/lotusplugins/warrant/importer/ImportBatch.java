package com.bradenkennedy.punishment.importer;

import java.util.ArrayList;
import java.util.List;

public record ImportBatch(List<ImportRecord> records, int skipped, int failed) {

    public ImportBatch {
        records = List.copyOf(records);
    }

    public ImportBatch plus(ImportBatch other) {
        List<ImportRecord> merged = new ArrayList<>(records);
        merged.addAll(other.records);
        return new ImportBatch(merged, skipped + other.skipped, failed + other.failed);
    }
}
