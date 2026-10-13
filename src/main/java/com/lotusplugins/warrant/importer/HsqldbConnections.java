package com.lotusplugins.warrant.importer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import org.hsqldb.jdbc.JDBCDriver;

final class HsqldbConnections {

    private HsqldbConnections() {}

    static Connection readOnly(Path databaseFile) throws SQLException {
        Properties credentials = new Properties();
        credentials.setProperty("user", "SA");
        credentials.setProperty("password", "");
        Connection connection = new JDBCDriver()
                .connect("jdbc:hsqldb:file:" + databaseFile + ";readonly=true;hsqldb.lock_file=false", credentials);
        if (connection == null) throw new SQLException("HSQLDB driver rejected " + databaseFile);
        return connection;
    }
}
