package com.bradenkennedy.punishment.storage;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.BaseDataType;
import com.j256.ormlite.support.DatabaseResults;

import java.sql.SQLException;
import java.time.Instant;

public class InstantPersister extends BaseDataType {

    private static final InstantPersister singleton = new InstantPersister();

    public static InstantPersister getSingleton() {
        return singleton;
    }

    private InstantPersister() {
        super(SqlType.LONG, new Class<?>[] { Instant.class });
    }

    @Override
    public Object parseDefaultString(FieldType fieldType, String defaultStr) {
        return Long.parseLong(defaultStr);
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
    public Object javaToSqlArg(FieldType fieldType, Object javaObject) {
        return ((Instant) javaObject).toEpochMilli();
    }

    @Override
    public boolean isAppropriateId() {
        return false;
    }
}