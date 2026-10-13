package com.bradenkennedy.punishment.importer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.bukkit.configuration.file.YamlConfiguration;
import org.hsqldb.jdbc.JDBCDriver;

public final class AdvancedBanConnection {

    private static final String HSQLDB_USER = "SA";

    private AdvancedBanConnection() {}

    public static Connection open(File advancedBanFolder) throws SQLException {
        boolean mysql = YamlConfiguration.loadConfiguration(new File(advancedBanFolder, "config.yml"))
                .getBoolean("UseMySQL", false);
        return mysql ? openMysql(advancedBanFolder) : openHsqldb(advancedBanFolder);
    }

    static String hsqldbUrl(File advancedBanFolder) {
        return "jdbc:hsqldb:file:" + new File(advancedBanFolder, "data/storage").getPath()
                + ";readonly=true;hsqldb.lock_file=false";
    }

    static String mysqlUrl(YamlConfiguration settings) {
        return "jdbc:mysql://" + settings.getString("MySQL.IP", "localhost")
                + ":" + settings.getInt("MySQL.Port", 3306)
                + "/" + settings.getString("MySQL.DB-Name", "")
                + "?" + settings.getString("MySQL.Properties", "useSSL=false&useUnicode=true&characterEncoding=utf8");
    }

    private static Connection openHsqldb(File advancedBanFolder) throws SQLException {
        Properties credentials = new Properties();
        credentials.setProperty("user", HSQLDB_USER);
        credentials.setProperty("password", "");
        Connection connection = new JDBCDriver().connect(hsqldbUrl(advancedBanFolder), credentials);
        if (connection == null) throw new SQLException("HSQLDB driver rejected the AdvancedBan data URL");
        return connection;
    }

    private static Connection openMysql(File advancedBanFolder) throws SQLException {
        YamlConfiguration settings = YamlConfiguration.loadConfiguration(new File(advancedBanFolder, "MySQL.yml"));
        return DriverManager.getConnection(
                mysqlUrl(settings), settings.getString("MySQL.Username"), settings.getString("MySQL.Password"));
    }
}
