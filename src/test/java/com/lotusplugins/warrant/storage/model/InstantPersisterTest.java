package com.bradenkennedy.punishment.storage.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstantPersisterTest {

    private static final Instant INSTANT = Instant.ofEpochMilli(1_700_000_000_123L);

    private final InstantPersister persister = InstantPersister.getSingleton();

    @SuppressWarnings("unused")
    private static final class Fields {
        Instant instant;
        String text;
    }

    @Test
    void singletonIsShared() {
        assertSame(persister, InstantPersister.getSingleton());
    }

    @Test
    void javaToSqlArgReturnsEpochMillis() {
        assertEquals(INSTANT.toEpochMilli(), persister.javaToSqlArg(null, INSTANT));
    }

    @Test
    void sqlArgToJavaReturnsInstant() {
        assertEquals(INSTANT, persister.sqlArgToJava(null, INSTANT.toEpochMilli(), 0));
    }

    @Test
    void roundTripTruncatesToMillis() {
        Instant withNanos = Instant.ofEpochSecond(1_700_000_000L, 123_456_789);
        Object sqlArg = persister.javaToSqlArg(null, withNanos);

        assertEquals(Instant.ofEpochMilli(1_700_000_000_123L), persister.sqlArgToJava(null, sqlArg, 0));
    }

    @Test
    void parseDefaultStringReadsEpochMillis() {
        assertEquals(Instant.ofEpochMilli(1000), persister.parseDefaultString(null, "1000"));
    }

    @Test
    void parseDefaultStringRejectsNonNumeric() {
        assertThrows(NumberFormatException.class, () -> persister.parseDefaultString(null, "tomorrow"));
    }

    @Test
    void isValidForFieldAcceptsInstantOnly() throws NoSuchFieldException {
        assertTrue(persister.isValidForField(Fields.class.getDeclaredField("instant")));
        assertFalse(persister.isValidForField(Fields.class.getDeclaredField("text")));
    }

    @Test
    void resultToSqlArgReadsStoredInstantFromDatabase() throws Exception {
        ConnectionSource source = new JdbcConnectionSource("jdbc:h2:mem:" + UUID.randomUUID());
        try {
            TableUtils.createTable(source, PunishmentModel.class);
            Dao<PunishmentModel, UUID> dao = DaoManager.createDao(source, PunishmentModel.class);
            PunishmentModel model = new PunishmentModel();
            model.setUuid(UUID.randomUUID());
            model.setIssuedAt(INSTANT);
            model.setExpiry(INSTANT.plusSeconds(60));
            dao.create(model);

            PunishmentModel loaded = dao.queryForId(model.getUuid());

            assertEquals(INSTANT, loaded.getIssuedAt());
            assertEquals(INSTANT.plusSeconds(60), loaded.getExpiry());
        } finally {
            source.close();
        }
    }
}
