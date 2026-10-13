package com.lotusplugins.warrant.importer;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;

public record LiteBansDatabase(Connection connection, String tablePrefix, Closeable cleanup) implements AutoCloseable {

    private static final String DEFAULT_PREFIX = "litebans_";
    private static final String H2_FILE = "litebans.mv.db";

    public LiteBansDatabase(Connection connection, String tablePrefix) {
        this(connection, tablePrefix, () -> {});
    }

    public static LiteBansDatabase open(File liteBansFolder, LegacyH2 legacyH2) throws IOException, SQLException {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(liteBansFolder, "config.yml"));
        String driver = config.getString("sql.driver", "H2").toLowerCase(Locale.ROOT);
        String prefix = config.getString("sql.table_prefix", DEFAULT_PREFIX);
        if (!prefix.matches("\\w*")) throw new SQLException("LiteBans table_prefix contains unsupported characters");
        String user = config.getString("sql.username", "");
        String password = config.getString("sql.password", "");
        if (driver.equals("h2")) {
            LegacyH2.Opened opened = legacyH2.open(new File(liteBansFolder, H2_FILE).toPath(), user, password);
            return new LiteBansDatabase(opened.connection(), prefix, opened.cleanup());
        }
        String scheme =
                switch (driver) {
                    case "mysql", "mariadb" -> "mysql";
                    case "postgresql" -> "postgresql";
                    default -> throw new SQLException("LiteBans " + driver + " databases are not supported");
                };
        String url = "jdbc:" + scheme + "://" + config.getString("sql.address", "localhost") + "/"
                + config.getString("sql.database", "litebans");
        return new LiteBansDatabase(DriverManager.getConnection(url, user, password), prefix);
    }

    @Override
    public void close() throws SQLException, IOException {
        try {
            connection.close();
        } finally {
            cleanup.close();
        }
    }
}
