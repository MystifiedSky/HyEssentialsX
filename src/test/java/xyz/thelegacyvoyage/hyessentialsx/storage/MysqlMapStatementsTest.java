package xyz.thelegacyvoyage.hyessentialsx.storage;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class MysqlMapStatementsTest {
    @Test
    void fixedMapStatementsBindUntrustedKeysAsData() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             var schema = connection.createStatement()) {
            schema.execute("CREATE TABLE hex_warps (name TEXT, json TEXT)");
            schema.execute("CREATE TABLE hex_kits (name TEXT, json TEXT)");
            schema.execute("CREATE TABLE hex_shops (name TEXT, json TEXT)");
            schema.execute("CREATE TABLE hex_ipbans (ip TEXT, json TEXT)");
            String key = "x'); DROP TABLE hex_players; --";
            for (var table : MysqlStorageBackend.MapTable.values()) {
                schema.executeUpdate(table.deleteSql);
                try (var insert = connection.prepareStatement(table.insertSql)) {
                    insert.setString(1, key);
                    insert.setString(2, "{\"fixture\":true}");
                    assertEquals(1, insert.executeUpdate());
                }
                assertEquals(1, schema.executeUpdate(table.deleteSql));
            }
        }
    }
}
