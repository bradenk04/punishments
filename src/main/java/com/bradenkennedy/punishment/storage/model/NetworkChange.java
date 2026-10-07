package com.bradenkennedy.punishment.storage.model;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.UUID;
@DatabaseTable(tableName = "punishment_changes")
public class NetworkChange {
    @DatabaseField(id = true) public UUID id;
    @DatabaseField public UUID punishmentId;
    @DatabaseField public UUID target;
    @DatabaseField public String action;
    @DatabaseField public long created;
    public NetworkChange() {}
    public NetworkChange(UUID punishmentId, UUID target, String action) {
        this.id = UUID.randomUUID(); this.punishmentId = punishmentId; this.target = target;
        this.action = action; this.created = System.currentTimeMillis();
    }
}
