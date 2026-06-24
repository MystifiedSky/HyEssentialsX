package xyz.thelegacyvoyage.hyessentialsx.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionHouseDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffActivityLogDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
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

public final class SqliteStorageBackend implements StorageBackend {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String url;
    private boolean available;
    private volatile boolean readOnly;
    private volatile boolean loggedReadOnly;

    public SqliteStorageBackend(@Nonnull Path dbFile) {
        this.url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        detectReadOnly(dbFile);
        init();
        if (!available) {
            throw new IllegalStateException("SQLite backend unavailable");
        }
    }

    private void init() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            Log.warn("SQLite driver not found: " + e.getMessage());
        }
        try (Connection conn = open()) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_players (uuid TEXT PRIMARY KEY, json TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_warps (name TEXT PRIMARY KEY, json TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_kits (name TEXT PRIMARY KEY, json TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_shops (name TEXT PRIMARY KEY, json TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_ipbans (ip TEXT PRIMARY KEY, json TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_auctionhouse (id TEXT PRIMARY KEY, json TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS hex_staff_activity (id TEXT PRIMARY KEY, json TEXT)");
            }
            available = true;
        } catch (Exception e) {
            Log.warn("SQLite unavailable, falling back to JSON: " + e.getMessage());
            available = false;
        }
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection(url);
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
            Log.warn("Failed to load player data from SQLite: " + e.getMessage());
            return null;
        }
        return new PlayerDataModel();
    }

    @Override
    public void savePlayerData(@Nonnull UUID uuid, @Nonnull PlayerDataModel data) {
        if (!available || readOnly) return;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO hex_players (uuid, json) VALUES (?, ?) " +
                             "ON CONFLICT(uuid) DO UPDATE SET json = excluded.json")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, gson.toJson(data));
            ps.executeUpdate();
        } catch (Exception e) {
            if (handleWriteException(e)) return;
            Log.warn("Failed to save player data to SQLite: " + e.getMessage());
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
            Log.warn("Failed to load warps from SQLite: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void saveWarps(@Nonnull Map<String, WarpModel> warps) {
        if (!available || readOnly) return;
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM hex_warps");
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
            if (handleWriteException(e)) return;
            Log.warn("Failed to save warps to SQLite: " + e.getMessage());
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
            Log.warn("Failed to load kits from SQLite: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void saveKits(@Nonnull Map<String, KitModel> kits) {
        if (!available || readOnly) return;
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM hex_kits");
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
            if (handleWriteException(e)) return;
            Log.warn("Failed to save kits to SQLite: " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public Map<String, ShopModel> loadShops() {
        Map<String, ShopModel> out = new HashMap<>();
        if (!available) return out;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT name, json FROM hex_shops")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String json = rs.getString(2);
                    ShopModel model = gson.fromJson(json, ShopModel.class);
                    if (name != null && model != null) out.put(name, model);
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to load shops from SQLite: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void saveShops(@Nonnull Map<String, ShopModel> shops) {
        if (!available) return;
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM hex_shops");
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO hex_shops (name, json) VALUES (?, ?)")) {
                for (Map.Entry<String, ShopModel> entry : shops.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, gson.toJson(entry.getValue()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            Log.warn("Failed to save shops to SQLite: " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public Map<String, IpBanModel> loadIpBans() {
        Map<String, IpBanModel> out = new HashMap<>();
        if (!available) return out;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT ip, json FROM hex_ipbans")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ip = rs.getString(1);
                    String json = rs.getString(2);
                    IpBanModel model = gson.fromJson(json, IpBanModel.class);
                    if (ip != null && model != null) out.put(ip, model);
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to load ip bans from SQLite: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void saveIpBans(@Nonnull Map<String, IpBanModel> bans) {
        if (!available || readOnly) return;
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM hex_ipbans");
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO hex_ipbans (ip, json) VALUES (?, ?)")) {
                for (Map.Entry<String, IpBanModel> entry : bans.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, gson.toJson(entry.getValue()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            if (handleWriteException(e)) return;
            Log.warn("Failed to save ip bans to SQLite: " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public AuctionHouseDataModel loadAuctionHouseData() {
        if (!available) return new AuctionHouseDataModel();
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT json FROM hex_auctionhouse WHERE id = ?")) {
            ps.setString(1, "global");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AuctionHouseDataModel data = gson.fromJson(rs.getString(1), AuctionHouseDataModel.class);
                    return data != null ? data : new AuctionHouseDataModel();
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to load auction house data from SQLite: " + e.getMessage());
        }
        return new AuctionHouseDataModel();
    }

    @Override
    public void saveAuctionHouseData(@Nonnull AuctionHouseDataModel data) {
        if (!available || readOnly) return;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO hex_auctionhouse (id, json) VALUES (?, ?) " +
                             "ON CONFLICT(id) DO UPDATE SET json = excluded.json")) {
            ps.setString(1, "global");
            ps.setString(2, gson.toJson(data));
            ps.executeUpdate();
        } catch (Exception e) {
            if (handleWriteException(e)) return;
            Log.warn("Failed to save auction house data to SQLite: " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public StaffActivityLogDataModel loadStaffActivityLog() {
        if (!available) return new StaffActivityLogDataModel();
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT json FROM hex_staff_activity WHERE id = ?")) {
            ps.setString(1, "global");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StaffActivityLogDataModel data = gson.fromJson(rs.getString(1), StaffActivityLogDataModel.class);
                    return data != null ? data : new StaffActivityLogDataModel();
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to load staff activity from SQLite: " + e.getMessage());
        }
        return new StaffActivityLogDataModel();
    }

    @Override
    public void saveStaffActivityLog(@Nonnull StaffActivityLogDataModel data) {
        if (!available || readOnly) return;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO hex_staff_activity (id, json) VALUES (?, ?) " +
                             "ON CONFLICT(id) DO UPDATE SET json = excluded.json")) {
            ps.setString(1, "global");
            ps.setString(2, gson.toJson(data));
            ps.executeUpdate();
        } catch (Exception e) {
            if (handleWriteException(e)) return;
            Log.warn("Failed to save staff activity to SQLite: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        // no-op
    }

    private void detectReadOnly(@Nonnull Path dbFile) {
        try {
            Path parent = dbFile.getParent();
            if (parent != null && Files.exists(parent) && !Files.isWritable(parent)) {
                readOnly = true;
                logReadOnly(dbFile);
                return;
            }
            if (Files.exists(dbFile) && !Files.isWritable(dbFile)) {
                readOnly = true;
                logReadOnly(dbFile);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean handleWriteException(@Nonnull Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase();
        if (lower.contains("sqlite_readonly") || lower.contains("readonly")) {
            readOnly = true;
            logReadOnly(null);
            return true;
        }
        return false;
    }

    private void logReadOnly(Path dbFile) {
        if (loggedReadOnly) return;
        loggedReadOnly = true;
        String pathInfo = dbFile != null ? (" (" + dbFile.toAbsolutePath() + ")") : "";
        Log.error("SQLite database is read-only" + pathInfo +
                ". Writes have been disabled to avoid log spam. Fix file permissions or switch storage to JSON/MySQL.");
    }
}

