package xyz.thelegacyvoyage.hyessentialsx.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MysqlStorageBackend implements StorageBackend {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String url;
    private final String user;
    private final String pass;
    private boolean available;

    public MysqlStorageBackend(@Nonnull String host,
                               int port,
                               @Nonnull String database,
                               @Nonnull String user,
                               @Nonnull String pass) {
        this.url = "jdbc:mariadb://" + host + ":" + port + "/" + database;
        this.user = user;
        this.pass = pass;
        init();
        if (!available) {
            throw new IllegalStateException("MySQL backend unavailable");
        }
    }

    private void init() {
        ensureDriverLoaded();
        try (Connection conn = open()) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_players (uuid VARCHAR(36) PRIMARY KEY, json LONGTEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_warps (name VARCHAR(64) PRIMARY KEY, json LONGTEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_kits (name VARCHAR(64) PRIMARY KEY, json LONGTEXT)");
            }
            available = true;
        } catch (Exception e) {
            Log.warn("MySQL unavailable, falling back to JSON: " + e.getMessage());
            available = false;
        }
    }



    private void ensureDriverLoaded() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            Log.warn("MariaDB driver not found on classpath.");
        }
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection(url, user, pass);
    }

    @Override
    @Nullable
    public PlayerDataModel loadPlayerData(@Nonnull UUID uuid) {
        if (!available) return new PlayerDataModel();
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT json FROM hex_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString(1);
                    PlayerDataModel data = gson.fromJson(json, PlayerDataModel.class);
                    return data != null ? data : new PlayerDataModel();
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to load player data from MySQL: " + e.getMessage());
        }
        return new PlayerDataModel();
    }

    @Override
    public void savePlayerData(@Nonnull UUID uuid, @Nonnull PlayerDataModel data) {
        if (!available) return;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO hex_players (uuid, json) VALUES (?, ?) ON DUPLICATE KEY UPDATE json = VALUES(json)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, gson.toJson(data));
            ps.executeUpdate();
        } catch (Exception e) {
            Log.warn("Failed to save player data to MySQL: " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public Set<UUID> listPlayerIds() {
        Set<UUID> ids = new HashSet<>();
        if (!available) return ids;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM hex_players")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        ids.add(UUID.fromString(rs.getString(1)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    @Override
    @Nonnull
    public Map<String, WarpModel> loadWarps() {
        Map<String, WarpModel> out = new HashMap<>();
        if (!available) return out;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT name, json FROM hex_warps")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String json = rs.getString(2);
                    WarpModel model = gson.fromJson(json, WarpModel.class);
                    if (name != null && model != null) out.put(name, model);
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to load warps from MySQL: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void saveWarps(@Nonnull Map<String, WarpModel> warps) {
        if (!available) return;
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("TRUNCATE TABLE hex_warps");
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO hex_warps (name, json) VALUES (?, ?)")) {
                for (Map.Entry<String, WarpModel> entry : warps.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, gson.toJson(entry.getValue()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            Log.warn("Failed to save warps to MySQL: " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public Map<String, KitModel> loadKits() {
        Map<String, KitModel> out = new HashMap<>();
        if (!available) return out;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT name, json FROM hex_kits")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String json = rs.getString(2);
                    KitModel model = gson.fromJson(json, KitModel.class);
                    if (name != null && model != null) out.put(name, model);
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to load kits from MySQL: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void saveKits(@Nonnull Map<String, KitModel> kits) {
        if (!available) return;
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("TRUNCATE TABLE hex_kits");
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO hex_kits (name, json) VALUES (?, ?)")) {
                for (Map.Entry<String, KitModel> entry : kits.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, gson.toJson(entry.getValue()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            Log.warn("Failed to save kits to MySQL: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        // no-op
    }
}
