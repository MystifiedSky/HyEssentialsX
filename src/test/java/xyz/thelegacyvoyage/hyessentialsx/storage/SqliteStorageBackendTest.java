package xyz.thelegacyvoyage.hyessentialsx.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteStorageBackendTest {

    @TempDir
    Path tempDir;

    @Test
    void configureConnectionSetsLockFriendlyPragmas() throws Exception {
        Path db = tempDir.resolve("hyessentialsx.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            SqliteStorageBackend.configureConnection(conn);

            assertEquals("wal", queryString(conn, "PRAGMA journal_mode"));
            assertEquals(1, queryInt(conn, "PRAGMA foreign_keys"));
            assertEquals(5000, queryInt(conn, "PRAGMA busy_timeout"));

            int synchronous = queryInt(conn, "PRAGMA synchronous");
            assertEquals(1, synchronous);
        }
    }

    private static String queryString(Connection conn, String sql) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private static int queryInt(Connection conn, String sql) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}
