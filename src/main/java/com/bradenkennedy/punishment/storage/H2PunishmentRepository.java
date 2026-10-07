package com.bradenkennedy.punishment.storage;
import com.bradenkennedy.punishment.storage.model.PunishmentModel;
import com.j256.ormlite.dao.Dao;
import java.io.File;
import java.sql.SQLException;
import java.util.UUID;
public final class H2PunishmentRepository extends JdbcPunishmentRepository {
    /** Retained for compatibility; operations use the instance DAO. */
    @Deprecated public static Dao<PunishmentModel, UUID> punishmentDao;
    public H2PunishmentRepository(File folder) throws SQLException {
        super("jdbc:h2:" + new File(folder, "punishments").getAbsolutePath(), "", "");
        punishmentDao = dao;
    }
}
