package com.bradenkennedy.punishment.storage.model;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.UUID;
@DatabaseTable(tableName = "punishment_players")
public class PlayerName {
    @DatabaseField(id = true) public UUID id;
    @DatabaseField public String name;
    public PlayerName() {}
    public PlayerName(UUID id, String name) { this.id = id; this.name = name; }
}
