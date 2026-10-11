package com.bradenkennedy.punishment.storage.model;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.BaseDataType;
import com.j256.ormlite.support.DatabaseResults;
import java.sql.SQLException;
import java.time.Instant;

public class InstantPersister extends BaseDataType {
    private static final InstantPersister INSTANCE = new InstantPersister();

    private InstantPersister() {
        super(SqlType.LONG, new Class<?>[] {Instant.class});
    }

    public static InstantPersister getSingleton() {
        return INSTANCE;
    }

    @Override
    public Object parseDefaultString(FieldType fieldType, String defaultStr) {
        return Instant.ofEpochMilli(Long.parseLong(defaultStr));
    }

    @Override
    public Object resultToSqlArg(FieldType fieldType, DatabaseResults results, int columnPos) throws SQLException {
        return results.getLong(columnPos);
    }

    @Override
    public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos) {
        return Instant.ofEpochMilli((Long) sqlArg);
    }

    @Override
    public Object javaToSqlArg(FieldType fieldType, Object obj) {
        return ((Instant) obj).toEpochMilli();
    }

    @Override
    public boolean isValidForField(java.lang.reflect.Field field) {
        return field.getType() == Instant.class;
    }
}
