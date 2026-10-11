package com.bradenkennedy.punishment.storage.model;

import java.util.UUID;

public record NetworkChange(UUID id, UUID punishmentId, UUID target, String action, long created) {}
