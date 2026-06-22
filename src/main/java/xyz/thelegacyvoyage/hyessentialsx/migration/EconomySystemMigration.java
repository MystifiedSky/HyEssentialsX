package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public final class EconomySystemMigration extends ModMigration {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DEFAULT_MYSQL_HOST = "localhost";
    private static final int DEFAULT_MYSQL_PORT = 3306;
    private static final String DEFAULT_MYSQL_DATABASE = "theeconomy";
    private static final String DEFAULT_MYSQL_USERNAME = "root";
    private static final String DEFAULT_MYSQL_PASSWORD = "";
    private static final String DEFAULT_MYSQL_TABLE = "bank";

    public EconomySystemMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "TheEconomy");
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
    public List<UserEntry> migrateUsers() {
        Map<UUID, MutableUser> users = new LinkedHashMap<>();
        Settings settings = readSettings();

        int imported = 0;
        if (settings.enableMySql) {
            imported += importFromMySql(users, settings);
        }
        if (users.isEmpty()) {
            imported += importFromJson(users);
        }
        if (users.isEmpty() && settings.enableMySql) {
            imported += importFromMySql(users, settings);
        }

        if (users.isEmpty()) {
            Log.warn("[HyEssentialsX] No TheEconomy balances found in " + sourceDir.toAbsolutePath());
            return List.of();
        }

        List<UserEntry> result = new ArrayList<>(users.size());
        for (MutableUser user : users.values()) {
            result.add(new UserEntry(
                    user.uuid,
                    user.name,
                    user.balance,
                    null,
                    null,
                    null
            ));
        }
        Log.info("[HyEssentialsX] Migrated " + result.size()
                + " users from TheEconomy (" + imported + " records processed)");
        return result;
    }

    private int importFromJson(@Nonnull Map<UUID, MutableUser> users) {
        Path balancesPath = findBalancesPath();
        if (balancesPath == null) {
            return 0;
        }

        int imported = 0;
        try (Reader reader = Files.newBufferedReader(balancesPath, StandardCharsets.UTF_8)) {
            JsonElement rootElement = GSON.fromJson(reader, JsonElement.class);
            JsonArray values = extractValuesArray(rootElement);
            if (values == null) {
                return 0;
            }

            for (JsonElement element : values) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                UUID uuid = parseUuid(getString(obj, "UUID", "uuid", "Uuid"));
                if (uuid == null) {
                    continue;
                }
                String nick = getString(obj, "Nick", "nick", "Nickname", "nickname", "name", "player");
                Long balance = parseBalance(getDouble(obj, "Balance", "balance", "Money", "money"));
                if (mergeUser(users, uuid, nick, balance)) {
                    imported++;
                }
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to parse TheEconomy balances file: " + e.getMessage());
            return 0;
        }

        if (imported > 0) {
            Log.info("[HyEssentialsX] TheEconomy JSON migration imported " + imported + " players");
        }
        return imported;
    }

    private int importFromMySql(@Nonnull Map<UUID, MutableUser> users,
                                @Nonnull Settings settings) {
        String host = blankToDefault(settings.mysqlHost, DEFAULT_MYSQL_HOST);
        int port = settings.mysqlPort > 0 ? settings.mysqlPort : DEFAULT_MYSQL_PORT;
        String database = blankToDefault(settings.mysqlDatabase, DEFAULT_MYSQL_DATABASE);
        String username = blankToDefault(settings.mysqlUser, DEFAULT_MYSQL_USERNAME);
        String password = settings.mysqlPassword == null ? DEFAULT_MYSQL_PASSWORD : settings.mysqlPassword;
        String table = sanitizeIdentifier(settings.mysqlTable);

        String jdbc = "jdbc:mariadb://" + host + ":" + port + "/" + database;
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);

        int imported = 0;
        String sql = "SELECT UUID, Nickname, Balance FROM `" + table + "`";
        try (Connection connection = DriverManager.getConnection(jdbc, props);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("UUID"));
                if (uuid == null) {
                    continue;
                }
                String nick = normalize(rs.getString("Nickname"));
                Long balance = parseBalance(rs.getDouble("Balance"));
                if (mergeUser(users, uuid, nick, balance)) {
                    imported++;
                }
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read TheEconomy MySQL table '" + table + "': " + e.getMessage());
            return 0;
        }

        if (imported > 0) {
            Log.info("[HyEssentialsX] TheEconomy MySQL migration imported " + imported + " players");
        }
        return imported;
    }

    @Nullable
    private Path findBalancesPath() {
        List<Path> candidates = List.of(
                sourceDir.resolve("Balances.json"),
                sourceDir.resolve("balances.json"),
                sourceDir.resolve("Balance.json"),
                sourceDir.resolve("balance.json"),
                sourceDir.resolve("data").resolve("Balances.json"),
                sourceDir.resolve("data").resolve("balances.json")
        );
        for (Path path : candidates) {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    @Nullable
    private static JsonArray extractValuesArray(@Nullable JsonElement rootElement) {
        if (rootElement == null || rootElement.isJsonNull()) {
            return null;
        }
        if (rootElement.isJsonArray()) {
            return rootElement.getAsJsonArray();
        }
        if (!rootElement.isJsonObject()) {
            return null;
        }
        JsonObject root = rootElement.getAsJsonObject();
        if (root.has("Values") && root.get("Values").isJsonArray()) {
            return root.getAsJsonArray("Values");
        }
        if (root.has("values") && root.get("values").isJsonArray()) {
            return root.getAsJsonArray("values");
        }
        return null;
    }

    @Nonnull
    private Settings readSettings() {
        Settings defaults = new Settings();
        Path configPath = findConfigPath();
        if (configPath == null) {
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return defaults;
            }
            Settings settings = new Settings();
            settings.enableMySql = getBoolean(root, defaults.enableMySql, "EnableMySQL", "enableMySQL", "mysqlEnabled");
            settings.mysqlHost = blankToDefault(getString(root, "MySQLHost", "mysqlHost"), defaults.mysqlHost);
            settings.mysqlPort = positiveInt(getInt(root, "MySQLPort", "mysqlPort"), defaults.mysqlPort);
            settings.mysqlDatabase = blankToDefault(getString(root, "MySQLDatabaseName", "mysqlDatabaseName", "MySQLDatabase", "mysqlDatabase"), defaults.mysqlDatabase);
            settings.mysqlUser = blankToDefault(getString(root, "MySQLUser", "mysqlUser", "MySQLUsername", "mysqlUsername"), defaults.mysqlUser);
            settings.mysqlPassword = blankToDefault(getString(root, "MySQLPassword", "mysqlPassword"), defaults.mysqlPassword);
            settings.mysqlTable = blankToDefault(getString(root, "MySQLTableName", "mysqlTableName"), defaults.mysqlTable);
            return settings;
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read TheEconomy config file: " + e.getMessage());
            return defaults;
        }
    }

    @Nullable
    private Path findConfigPath() {
        List<Path> candidates = List.of(
                sourceDir.resolve("EconomySystem.json"),
                sourceDir.resolve("economysystem.json"),
                sourceDir.resolve("config.json"),
                sourceDir.resolve("EconomyConfig.json"),
                sourceDir.resolve("config").resolve("EconomySystem.json"),
                sourceDir.resolve("config").resolve("economysystem.json"),
                sourceDir.resolve("config").resolve("config.json")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        try (var stream = Files.list(sourceDir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(path)) continue;
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".json")) continue;
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root == null) continue;
                    if (root.has("EnableMySQL")
                            || root.has("enableMySQL")
                            || root.has("MySQLTableName")
                            || root.has("mysqlTableName")) {
                        return path;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean mergeUser(@Nonnull Map<UUID, MutableUser> users,
                              @Nonnull UUID uuid,
                              @Nullable String name,
                              @Nullable Long balance) {
        MutableUser existing = users.get(uuid);
        boolean changed = false;
        if (existing == null) {
            existing = new MutableUser(uuid);
            users.put(uuid, existing);
            changed = true;
        }

        String normalizedName = normalize(name);
        if (normalizedName != null && (existing.name == null || existing.name.isBlank())) {
            existing.name = normalizedName;
            changed = true;
        }

        if (balance != null) {
            long clamped = Math.max(0L, balance);
            if (existing.balance == null || clamped > existing.balance) {
                existing.balance = clamped;
                changed = true;
            }
        }
        return changed;
    }

    @Nullable
    private static String getString(@Nonnull JsonObject obj, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.get(key).isJsonNull()) continue;
                String value = obj.get(key).getAsString();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static int positiveInt(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    @Nullable
    private static Integer getInt(@Nonnull JsonObject obj, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.get(key).isJsonNull()) continue;
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean getBoolean(@Nonnull JsonObject obj, boolean fallback, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.get(key).isJsonNull()) continue;
                return obj.get(key).getAsBoolean();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    @Nullable
    private static Double getDouble(@Nonnull JsonObject obj, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.get(key).isJsonNull()) continue;
                return obj.get(key).getAsDouble();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Long parseBalance(@Nullable Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        if (value <= 0.0D) {
            return 0L;
        }
        if (value >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(value);
    }

    @Nonnull
    private static String blankToDefault(@Nullable String value, @Nonnull String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    @Nonnull
    private static String sanitizeIdentifier(@Nullable String raw) {
        String value = blankToDefault(raw, DEFAULT_MYSQL_TABLE).trim();
        if (value.isBlank()) {
            return DEFAULT_MYSQL_TABLE;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            if (!valid) {
                return DEFAULT_MYSQL_TABLE;
            }
        }
        return value;
    }

    private static final class Settings {
        boolean enableMySql = false;
        String mysqlHost = DEFAULT_MYSQL_HOST;
        int mysqlPort = DEFAULT_MYSQL_PORT;
        String mysqlDatabase = DEFAULT_MYSQL_DATABASE;
        String mysqlUser = DEFAULT_MYSQL_USERNAME;
        String mysqlPassword = DEFAULT_MYSQL_PASSWORD;
        String mysqlTable = DEFAULT_MYSQL_TABLE;
    }

    private static final class MutableUser {
        private final UUID uuid;
        private String name;
        private Long balance;

        private MutableUser(@Nonnull UUID uuid) {
            this.uuid = uuid;
        }
    }
}
