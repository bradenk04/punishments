package com.lotusplugins.warrant.importer;

import com.lotusplugins.warrant.api.model.Punishment;

public record ImportRecord(String sourceId, Punishment punishment) {}
