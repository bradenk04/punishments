package com.bradenkennedy.punishment.importer;

public record ImportReport(int imported, int skipped, int failed) {

    public ImportReport plus(ImportReport other) {
        return new ImportReport(imported + other.imported, skipped + other.skipped, failed + other.failed);
    }
}
