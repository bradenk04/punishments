package com.bradenkennedy.punishment.importer;

import com.bradenkennedy.punishment.api.model.Punishment;

public record ImportRecord(String sourceId, Punishment punishment) {}
