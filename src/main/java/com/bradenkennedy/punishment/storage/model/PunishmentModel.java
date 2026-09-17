package com.bradenkennedy.punishment.storage.model;

import java.time.Instant;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "punishments")
public class PunishmentModel {
    @DatabaseField(id = true)
    private UUID id;

    @DatabaseField
    private UUID target;

    @DatabaseField
    private PunishmentType type;

    @DatabaseField
    private UUID issuerId;

    @DatabaseField
    private Instant issuedAt;

    @DatabaseField
    private String reason;

    @DatabaseField
    private Instant expiry;

    @DatabaseField
    private boolean revoked;

    @DatabaseField
    private String revokeReason;

    @DatabaseField
    private UUID revokedBy;

    public PunishmentModel() {}
    public PunishmentModel(
        UUID id,
        PunishmentType type,
        PunishmentIssuer issuer,
        String reason,
        Instant expiry,
        boolean revoked,
        @Nullable String revokeReason,
        @Nullable UUID revokedBy
    ) {
        this.id = id;
        this.type = type;
        this.issuedAt = issuer.issuedAt();
        this.issuerId = issuer.issuer();
        this.reason = reason;
        this.expiry = expiry;
        this.revoked = revoked;
        this.revokeReason = revokeReason;
        this.revokedBy = revokedBy;
    }

    public PunishmentModel(Punishment punishment) {
        this(
            punishment.id(),
            punishment.type(),
            punishment.issuer(),
            punishment.reason(),
            punishment.expiry(),
            punishment.revoked(),
            null,
            null
        );
    }
    
}
