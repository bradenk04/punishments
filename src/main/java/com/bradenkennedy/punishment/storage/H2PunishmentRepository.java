package com.bradenkennedy.punishment.storage;

public class H2PunishmentRepository extends PunishmentRepository {
    public H2PunishmentRepository(File dataFolder) throws SQLException {
        String dbPath = new File(dataFolder, "punishments").getAbsolutePath();
        String url = "jdbc:h2:" + dbPath;

        this.connectionSource = new JdbcConnectionSource(url);
        this.punishmentDao = DaoManager.createDao(connectionSource, );

        TableUtils.createTableIfNotExists(connectionSource, );
    }    
}
