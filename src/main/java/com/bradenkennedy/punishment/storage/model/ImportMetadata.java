package com.bradenkennedy.punishment.storage.model;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.UUID;
@DatabaseTable(tableName = "punishment_imports")
public class ImportMetadata {
    @DatabaseField(id = true) public UUID id;
    @DatabaseField public String source;
    @DatabaseField public String originalId;
    @DatabaseField public String issuerName;
    @DatabaseField public String targetName;
    public ImportMetadata() {}
}
