package com.bradenkennedy.punishment.storage.model;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.api.model.Revocation;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

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

    @DatabaseField(persisterClass = InstantPersister.class)
    private Instant issuedAt;

    @DatabaseField
    private String reason;

    @DatabaseField(persisterClass = InstantPersister.class)
    private Instant expiry;

    @DatabaseField
    private boolean revoked;

    @DatabaseField
    private String revokedReason;

    @DatabaseField
    private UUID revokedBy;

    @DatabaseField(persisterClass = InstantPersister.class)
    private Instant revokedAt;

    @DatabaseField
    private boolean acknowledged;

    public PunishmentModel() {}

    public PunishmentModel(
            UUID id,
            PunishmentType type,
            PunishmentIssuer issuer,
            String reason,
            Instant expiry,
            boolean revoked,
            @Nullable String revokedReason,
            @Nullable UUID revokedBy,
            @Nullable Instant revokedAt) {
        this.id = id;
        this.type = type;
        this.issuedAt = issuer.issuedAt();
        this.issuerId = issuer.issuer();
        this.reason = reason;
        this.expiry = expiry;
        this.revoked = revoked;
        this.revokedReason = revokedReason;
        this.revokedBy = revokedBy;
        this.revokedAt = revokedAt;
    }

    public PunishmentModel(Punishment punishment) {
        this(
                punishment.id(),
                punishment.type(),
                punishment.issuer(),
                punishment.reason(),
                punishment.expiry(),
                punishment.revoked(),
                revocation(punishment, Revocation::reason),
                revocation(punishment, Revocation::by),
                revocation(punishment, Revocation::at));
        this.target = punishment.target();
    }

    private static <T> @Nullable T revocation(Punishment punishment, Function<Revocation, T> field) {
        return punishment.revoked() ? field.apply(punishment.revocation()) : null;
    }

    public UUID getUuid() {
        return this.id;
    }

    public void setUuid(UUID id) {
        this.id = id;
    }

    public UUID getTarget() {
        return this.target;
    }

    public void setTarget(UUID target) {
        this.target = target;
    }

    public PunishmentType getType() {
        return this.type;
    }

    public void setType(PunishmentType type) {
        this.type = type;
    }

    public UUID getIssuerUuid() {
        return this.issuerId;
    }

    public void setIssuerUuid(UUID issuerUuid) {
        this.issuerId = issuerUuid;
    }

    public Instant getIssuedAt() {
        return this.issuedAt;
    }

    public void setIssuedAt(Instant issueTime) {
        this.issuedAt = issueTime;
    }

    public String getReason() {
        return this.reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getExpiry() {
        return this.expiry;
    }

    public void setExpiry(Instant expiry) {
        this.expiry = expiry;
    }

    public boolean isRevoked() {
        return this.revoked;
    }

    public void setIsRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public String getRevokedReason() {
        return this.revokedReason;
    }

    public void setRevokedReason(String reason) {
        this.revokedReason = reason;
    }

    public UUID getRevokedBy() {
        return this.revokedBy;
    }

    public void setRevokedBy(UUID revokedBy) {
        this.revokedBy = revokedBy;
    }

    public Instant getRevokedAt() {
        return this.revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Punishment toPunishment() {
        return new Punishment(
                this.id,
                this.target,
                this.type,
                new PunishmentIssuer(this.issuerId, this.issuedAt),
                this.reason,
                this.expiry,
                this.revoked ? new Revocation(this.revokedBy, this.revokedReason, this.revokedAt) : null);
    }
}
