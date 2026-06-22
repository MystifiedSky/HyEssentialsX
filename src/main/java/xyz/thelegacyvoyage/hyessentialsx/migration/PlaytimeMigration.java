package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

public final class PlaytimeMigration extends ModMigration {

    private static final Gson GSON = new GsonBuilder().create();

    public PlaytimeMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "Playtime");
    }

    @Override
    @Nonnull
    public List<WarpModel> migrateWarps() {
        return List.of();
    }

    @Override
    @Nonnull
    public List<HomeEntry> migrateHomes() {
        return List.of();
    }

    @Override
    @Nonnull
    public List<UserEntry> migrateUsers() throws Exception {
        Path sqliteFile = sourceDir.resolve("playtime.db");
        if (Files.exists(sqliteFile)) {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toAbsolutePath())) {
                List<UserEntry> users = readUsersFromSessions(connection);
                Log.info("[HyEssentialsX] Migrated " + users.size() + " users from Playtime SQLite");
                return users;
            }
        }

        DatabaseSettings settings = readDatabaseSettings(sourceDir.resolve("config.json"));
        if (!"mysql".equalsIgnoreCase(settings.type)) {
            Log.warn("[HyEssentialsX] Playtime database not found: " + sqliteFile.toAbsolutePath());
            return List.of();
        }

        String host = blankToDefault(settings.host, "localhost");
        int port = settings.port > 0 ? settings.port : 3306;
        String database = blankToDefault(settings.databaseName, "playtime_db");
        String jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + database
                + "?useSsl=" + settings.useSSL;

        Properties props = new Properties();
        props.setProperty("user", blankToDefault(settings.username, "root"));
        props.setProperty("password", settings.password == null ? "" : settings.password);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, props)) {
            List<UserEntry> users = readUsersFromSessions(connection);
            Log.info("[HyEssentialsX] Migrated " + users.size() + " users from Playtime MySQL");
            return users;
        }
    }

    @Nonnull
    private List<UserEntry> readUsersFromSessions(@Nonnull Connection connection) throws Exception {
        String query = "SELECT uuid, MAX(username) AS username, SUM(duration) AS total_duration "
                + "FROM playtime_sessions GROUP BY uuid";
        List<UserEntry> users = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("uuid"));
                if (uuid == null) {
                    continue;
                }

                String username = rs.getString("username");
                long durationMs = rs.getLong("total_duration");
                if (rs.wasNull() || durationMs <= 0L) {
                    continue;
                }
                long playtimeSeconds = Math.max(0L, durationMs / 1000L);

                users.add(new UserEntry(
                        uuid,
                        normalize(username),
                        null,
                        null,
                        null,
                        playtimeSeconds
                ));
            }
        }
        return users;
    }

    @Nonnull
    private DatabaseSettings readDatabaseSettings(@Nonnull Path configPath) {
        DatabaseSettings defaults = new DatabaseSettings();
        if (!Files.exists(configPath)) {
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("database") || !root.get("database").isJsonObject()) {
                return defaults;
            }
            JsonObject db = root.getAsJsonObject("database");
            DatabaseSettings settings = new DatabaseSettings();
            settings.type = getString(db, "type", defaults.type).toLowerCase(Locale.ROOT);
            settings.host = getString(db, "host", defaults.host);
            settings.port = getInt(db, "port", defaults.port);
            settings.databaseName = getString(db, "databaseName", defaults.databaseName);
            settings.username = getString(db, "username", defaults.username);
            settings.password = getString(db, "password", defaults.password);
            settings.useSSL = getBoolean(db, "useSSL", defaults.useSSL);
            return settings;
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read Playtime config.json: " + e.getMessage());
            return defaults;
        }
    }

    @Nonnull
    private static String getString(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull String fallback) {
        try {
            if (!obj.has(key) || obj.get(key).isJsonNull()) {
                return fallback;
            }
            String value = obj.get(key).getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int getInt(@Nonnull JsonObject obj, @Nonnull String key, int fallback) {
        try {
            if (!obj.has(key) || obj.get(key).isJsonNull()) {
                return fallback;
            }
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(@Nonnull JsonObject obj, @Nonnull String key, boolean fallback) {
        try {
            if (!obj.has(key) || obj.get(key).isJsonNull()) {
                return fallback;
            }
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Nonnull
    private static String blankToDefault(String value, @Nonnull String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static final class DatabaseSettings {
        String type = "sqlite";
        String host = "localhost";
        int port = 3306;
        String databaseName = "playtime_db";
        String username = "root";
        String password = "";
        boolean useSSL = false;
    }
}
