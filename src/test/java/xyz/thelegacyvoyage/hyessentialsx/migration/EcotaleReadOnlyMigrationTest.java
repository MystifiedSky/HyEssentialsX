package xyz.thelegacyvoyage.hyessentialsx.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class EcotaleReadOnlyMigrationTest {
    @TempDir Path tempDir;

    @Test
    void readsLegacyDatabaseWithoutAllowingWrites() throws Exception {
        Path base = tempDir.resolve("ecotale");
        try (var connection = DriverManager.getConnection("jdbc:h2:" + base, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE balances (uuid VARCHAR, player_name VARCHAR, balance DOUBLE)");
            statement.execute("INSERT INTO balances VALUES ('fixture', 'Player', 12.5)");
        }
        byte[] before = Files.readAllBytes(tempDir.resolve("ecotale.mv.db"));
        try (var connection = EcotaleMigration.openLegacyH2(base);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT balance FROM balances")) {
            assertTrue(rows.next());
            assertEquals(12.5, rows.getDouble(1));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM balances"));
        }
        assertArrayEquals(before, Files.readAllBytes(tempDir.resolve("ecotale.mv.db")));
    }

    @Test
    void missingDatabaseIsNeverCreated() {
        Path base = tempDir.resolve("missing");
        assertThrows(SQLException.class, () -> EcotaleMigration.openLegacyH2(base));
        assertFalse(Files.exists(tempDir.resolve("missing.mv.db")));
    }

    @Test
    void externalTableIdentifiersCannotIntroduceSqlSyntax() throws Exception {
        var economy = EconomySystemMigration.class.getDeclaredMethod("sanitizeIdentifier", String.class);
        var ecotale = EcotaleMigration.class.getDeclaredMethod("sanitizeTablePrefix", String.class);
        economy.setAccessible(true);
        ecotale.setAccessible(true);
        for (String value : new String[]{"custom_123", "name;DROP TABLE balances", "a`b", "a b", "x--", "x'", "é"}) {
            assertTrue(((String) economy.invoke(null, value)).matches("[A-Za-z0-9_]+"));
            assertTrue(((String) ecotale.invoke(null, value)).matches("[A-Za-z0-9_]+"));
        }
    }
}
