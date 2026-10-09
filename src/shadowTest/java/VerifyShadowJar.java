import com.bradenkennedy.punishment.storage.H2PunishmentRepository;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.jar.JarFile;

class VerifyShadowJar {
    public static void main(String[] args) throws Exception {
        try (var jar = new JarFile(args[0])) {
            if (jar.stream().anyMatch(entry -> entry.getName().startsWith("org/h2/")
                    || entry.getName().startsWith("com/j256/"))) {
                throw new AssertionError("Unrelocated database classes in distributable");
            }
            for (String entry : new String[] {
                "com/bradenkennedy/punishment/libs/h2/Driver.class",
                "com/bradenkennedy/punishment/libs/ormlite/jdbc/JdbcConnectionSource.class",
                "META-INF/services/java.sql.Driver"
            }) {
                if (jar.getJarEntry(entry) == null) {
                    throw new AssertionError("Missing " + entry);
                }
            }
        }
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:shadow_smoke");
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT 1")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new AssertionError("H2 query failed");
            }
            if (!DriverManager.getDriver("jdbc:h2:mem:shadow_smoke").getClass().getName()
                    .equals("com.bradenkennedy.punishment.libs.h2.Driver")) {
                throw new AssertionError("Wrong JDBC driver loaded");
            }
        }
        var repository = new H2PunishmentRepository(Files.createTempDirectory("shadow-db-").toFile());
        try {
            if (!repository.findHistory(UUID.randomUUID()).isEmpty()) {
                throw new AssertionError("New database is not empty");
            }
        } finally {
            repository.connectionSource.close();
        }
    }
}
