package com.lotusplugins.warrant.importer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AdvancedBanConnection {

    private AdvancedBanConnection() {}

    public static Connection open(File advancedBanFolder) throws SQLException {
        boolean mysql = YamlConfiguration.loadConfiguration(new File(advancedBanFolder, "config.yml"))
                .getBoolean("UseMySQL", false);
        return mysql ? openMysql(advancedBanFolder) : openHsqldb(advancedBanFolder);
    }

    static String mysqlUrl(YamlConfiguration settings) {
        return "jdbc:mysql://" + settings.getString("MySQL.IP", "localhost")
                + ":" + settings.getInt("MySQL.Port", 3306)
                + "/" + settings.getString("MySQL.DB-Name", "")
                + "?" + settings.getString("MySQL.Properties", "useSSL=false&useUnicode=true&characterEncoding=utf8");
    }

    private static Connection openHsqldb(File advancedBanFolder) throws SQLException {
        return HsqldbConnections.readOnly(new File(advancedBanFolder, "data/storage").toPath());
    }

    private static Connection openMysql(File advancedBanFolder) throws SQLException {
        YamlConfiguration settings = YamlConfiguration.loadConfiguration(new File(advancedBanFolder, "MySQL.yml"));
        return DriverManager.getConnection(
                mysqlUrl(settings), settings.getString("MySQL.Username"), settings.getString("MySQL.Password"));
    }
}
