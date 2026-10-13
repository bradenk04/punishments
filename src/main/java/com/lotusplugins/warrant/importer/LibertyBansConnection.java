package com.bradenkennedy.punishment.importer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LibertyBansConnection {

    private static final String DEFAULT_USER = "defaultuser";
    private static final String DEFAULT_PASSWORD = "defaultpass";

    private LibertyBansConnection() {}

    public static Connection open(File libertyBansFolder) throws SQLException {
        YamlConfiguration sql = YamlConfiguration.loadConfiguration(new File(libertyBansFolder, "sql.yml"));
        String vendor = sql.getString("rdms-vendor", "HSQLDB").toUpperCase(Locale.ROOT);
        String user = sql.getString("auth-details.user", DEFAULT_USER);
        String password = sql.getString("auth-details.password", DEFAULT_PASSWORD);
        boolean unconfiguredRemote = user.equals(DEFAULT_USER) || password.equals(DEFAULT_PASSWORD);
        String scheme =
                switch (vendor) {
                    case "HSQLDB" -> "";
                    case "MARIADB", "MYSQL" -> "mysql";
                    case "POSTGRES", "COCKROACH" -> "postgresql";
                    default -> throw new SQLException("LibertyBans " + vendor + " databases are not supported");
                };
        if (scheme.isEmpty() || unconfiguredRemote) {
            return HsqldbConnections.readOnly(
                    new File(libertyBansFolder, "internal/hypersql/punishments-database").toPath());
        }
        String url = "jdbc:" + scheme + "://" + sql.getString("auth-details.host", "localhost") + ":"
                + sql.getInt("auth-details.port", 3306) + "/" + sql.getString("auth-details.database", "bans");
        return DriverManager.getConnection(url, user, password);
    }
}
