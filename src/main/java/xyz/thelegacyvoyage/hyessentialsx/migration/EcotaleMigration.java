package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
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
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.Decimal128;

public final class EcotaleMigration extends ModMigration {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DEFAULT_H2_FILE = "ecotale";
    private static final String DEFAULT_MYSQL_HOST = "localhost";
    private static final int DEFAULT_MYSQL_PORT = 3306;
    private static final String DEFAULT_MYSQL_DATABASE = "ecotale";
    private static final String DEFAULT_MYSQL_USERNAME = "root";
    private static final String DEFAULT_MYSQL_PASSWORD = "";
    private static final String DEFAULT_MYSQL_TABLE_PREFIX = "eco_";
    private static final String DEFAULT_MONGO_URI = "mongodb://localhost:27017";
    private static final String DEFAULT_MONGO_DATABASE = "ecotale";

    public EcotaleMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "Ecotale");
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
        EcotaleSettings settings = loadSettings();
        String provider = settings.storageProvider.toLowerCase(Locale.ROOT);
        if (provider.isBlank()) {
            provider = "h2";
        }

        int imported = 0;
        switch (provider) {
            case "mysql" -> imported += importFromMySql(users, settings);
            case "json" -> {
                imported += importFromJsonPlayers(users);
                imported += importFromLegacyBalances(users);
            }
            case "h2" -> imported += importFromH2(users);
            case "mongodb", "mongo" -> imported += importFromMongo(users, settings);
            default -> Log.warn("[HyEssentialsX] Unknown Ecotale storage provider '" + provider + "', trying local files.");
        }

        if (users.isEmpty()) {
            imported += importFromMongo(users, settings);
        }
        if (users.isEmpty()) {
            imported += importFromH2(users);
        }
        if (users.isEmpty()) {
            imported += importFromJsonPlayers(users);
            imported += importFromLegacyBalances(users);
        }

        if (users.isEmpty()) {
            Log.warn("[HyEssentialsX] No Ecotale player balances found in " + sourceDir.toAbsolutePath());
            return List.of();
        }

        List<UserEntry> result = new ArrayList<>(users.size());
        for (MutableUser user : users.values()) {
            result.add(new UserEntry(
                    user.uuid,
                    user.name,
                    user.balance,
                    user.lastSeenAt,
                    null,
                    null
            ));
        }
        Log.info("[HyEssentialsX] Migrated " + result.size() + " users from Ecotale (" + imported + " records processed)");
        return result;
    }

    private int importFromJsonPlayers(@Nonnull Map<UUID, MutableUser> users) {
        Path playersDir = sourceDir.resolve("players");
        if (!Files.exists(playersDir) || !Files.isDirectory(playersDir)) {
            return 0;
        }

        int imported = 0;
        try (var stream = Files.list(playersDir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".json")) {
                    continue;
                }
                if (name.endsWith("_settings.json")) {
                    continue;
                }
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                imported += importFromBalanceJsonFile(file, users);
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read Ecotale player directory: " + e.getMessage());
        }

        if (imported > 0) {
            Log.info("[HyEssentialsX] Ecotale JSON migration imported " + imported + " players");
        }
        return imported;
    }

    private int importFromMongo(@Nonnull Map<UUID, MutableUser> users,
                                @Nonnull EcotaleSettings settings) {
        String uri = blankToDefault(settings.mongoUri, DEFAULT_MONGO_URI);
        String databaseName = blankToDefault(settings.mongoDatabase, DEFAULT_MONGO_DATABASE);
        int imported = 0;

        try (MongoClient client = MongoClients.create(uri)) {
            MongoDatabase database = client.getDatabase(databaseName);
            MongoCollection<Document> balances = database.getCollection("balances");

            for (Document document : balances.find()) {
                UUID uuid = parseUuid(document.getString("uuid"));
                if (uuid == null) {
                    continue;
                }
                String playerName = normalize(firstNonBlank(
                        document.getString("player_name"),
                        document.getString("playerName")
                ));
                Long balance = parseBalance(documentDouble(document, "balance", "Balance"));
                Long lastSeenAt = parsePositiveLong(documentEpochMillis(document, "updated_at", "updatedAt"));
                if (mergeUser(users, uuid, playerName, balance, lastSeenAt)) {
                    imported++;
                }
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read Ecotale MongoDB balances: " + e.getMessage());
            return 0;
        }

        if (imported > 0) {
            Log.info("[HyEssentialsX] Ecotale MongoDB migration imported " + imported + " players");
        }
        return imported;
    }

    private int importFromLegacyBalances(@Nonnull Map<UUID, MutableUser> users) {
        Path legacy = sourceDir.resolve("balances.json");
        if (!Files.exists(legacy) || !Files.isRegularFile(legacy)) {
            return 0;
        }

        int imported = 0;
        try (Reader reader = Files.newBufferedReader(legacy, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return 0;
            }
            JsonArray balances = getArray(root, "Balances", "balances");
            if (balances == null || balances.isEmpty()) {
                return 0;
            }
            for (JsonElement element : balances) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                UUID uuid = parseUuid(firstNonBlank(
                        getString(obj, "Uuid", "uuid"),
                        getString(obj, "PlayerUuid", "playerUuid")
                ));
                if (uuid == null) {
                    continue;
                }
                Long balance = parseBalance(getDouble(obj, "Balance", "balance"));
                Long lastSeenAt = parsePositiveLong(getLong(obj, "LastTransactionTime", "lastTransactionTime"));
                if (mergeUser(users, uuid, null, balance, lastSeenAt)) {
                    imported++;
                }
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to parse Ecotale legacy balances.json: " + e.getMessage());
        }

        if (imported > 0) {
            Log.info("[HyEssentialsX] Ecotale legacy migration imported " + imported + " players");
        }
        return imported;
    }

    private int importFromBalanceJsonFile(@Nonnull Path file, @Nonnull Map<UUID, MutableUser> users) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) {
                return 0;
            }

            String fileName = file.getFileName().toString();
            String withoutExt = fileName.endsWith(".json")
                    ? fileName.substring(0, fileName.length() - 5)
                    : fileName;

            UUID uuid = parseUuid(firstNonBlank(
                    getString(obj, "Uuid", "uuid"),
                    withoutExt
            ));
            if (uuid == null) {
                return 0;
            }

            String playerName = getString(obj, "PlayerName", "playerName", "player_name");
            Long balance = parseBalance(getDouble(obj, "Balance", "balance"));
            Long lastSeenAt = parsePositiveLong(getLong(obj, "LastTransactionTime", "lastTransactionTime"));
            return mergeUser(users, uuid, playerName, balance, lastSeenAt) ? 1 : 0;
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read Ecotale file " + file.getFileName() + ": " + e.getMessage());
            return 0;
        }
    }

    private int importFromH2(@Nonnull Map<UUID, MutableUser> users) {
        Path h2File = sourceDir.resolve(DEFAULT_H2_FILE + ".mv.db");
        if (!Files.exists(h2File)) {
            return 0;
        }

        try {
            Class.forName("org.h2.Driver");
        } catch (Throwable ignored) {
            Log.warn("[HyEssentialsX] H2 driver is unavailable; cannot migrate Ecotale H2 storage.");
            return 0;
        }

        int imported = 0;
        String dbBase = sourceDir.resolve(DEFAULT_H2_FILE).toAbsolutePath().toString();
        String jdbc = "jdbc:h2:" + dbBase + ";MODE=MySQL;AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE";
        try (Connection connection = DriverManager.getConnection(jdbc, "sa", "");
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid, player_name, balance FROM balances"
             );
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("uuid"));
                if (uuid == null) {
                    continue;
                }
                String playerName = normalize(rs.getString("player_name"));
                Long balance = parseBalance(rs.getDouble("balance"));
                if (mergeUser(users, uuid, playerName, balance, null)) {
                    imported++;
                }
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read Ecotale H2 database: " + e.getMessage());
            return 0;
        }

        if (imported > 0) {
            Log.info("[HyEssentialsX] Ecotale H2 migration imported " + imported + " players");
        }
        return imported;
    }

    private int importFromMySql(@Nonnull Map<UUID, MutableUser> users,
                                @Nonnull EcotaleSettings settings) {
        String host = blankToDefault(settings.mysqlHost, DEFAULT_MYSQL_HOST);
        int port = settings.mysqlPort > 0 ? settings.mysqlPort : DEFAULT_MYSQL_PORT;
        String database = blankToDefault(settings.mysqlDatabase, DEFAULT_MYSQL_DATABASE);
        String username = blankToDefault(settings.mysqlUsername, DEFAULT_MYSQL_USERNAME);
        String password = settings.mysqlPassword == null ? DEFAULT_MYSQL_PASSWORD : settings.mysqlPassword;
        String tablePrefix = sanitizeTablePrefix(settings.mysqlTablePrefix);

        String jdbc = "jdbc:mariadb://" + host + ":" + port + "/" + database;
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);

        int imported = 0;
        String sql = "SELECT uuid, player_name, balance FROM " + tablePrefix + "balances";
        try (Connection connection = DriverManager.getConnection(jdbc, props);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("uuid"));
                if (uuid == null) {
                    continue;
                }
                String playerName = normalize(rs.getString("player_name"));
                Long balance = parseBalance(rs.getDouble("balance"));
                if (mergeUser(users, uuid, playerName, balance, null)) {
                    imported++;
                }
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read Ecotale MySQL balances: " + e.getMessage());
            return 0;
        }

        if (imported > 0) {
            Log.info("[HyEssentialsX] Ecotale MySQL migration imported " + imported + " players");
        }
        return imported;
    }

    @Nonnull
    private EcotaleSettings loadSettings() {
        EcotaleSettings defaults = new EcotaleSettings();
        Path configPath = findConfigPath();
        if (configPath == null) {
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return defaults;
            }
            EcotaleSettings settings = new EcotaleSettings();
            settings.storageProvider = blankToDefault(getString(root, "StorageProvider", "storageProvider"), defaults.storageProvider);
            settings.mysqlHost = blankToDefault(getString(root, "MysqlHost", "mysqlHost"), defaults.mysqlHost);
            settings.mysqlPort = parsePositiveInt(getInteger(root, "MysqlPort", "mysqlPort"), defaults.mysqlPort);
            settings.mysqlDatabase = blankToDefault(getString(root, "MysqlDatabase", "mysqlDatabase"), defaults.mysqlDatabase);
            settings.mysqlUsername = blankToDefault(getString(root, "MysqlUsername", "mysqlUsername"), defaults.mysqlUsername);
            settings.mysqlPassword = blankToDefault(getString(root, "MysqlPassword", "mysqlPassword"), defaults.mysqlPassword);
            settings.mysqlTablePrefix = blankToDefault(getString(root, "MysqlTablePrefix", "mysqlTablePrefix"), defaults.mysqlTablePrefix);
            settings.mongoUri = blankToDefault(getString(root, "MongoUri", "mongoUri", "MongoURI", "mongoURI"), defaults.mongoUri);
            settings.mongoDatabase = blankToDefault(getString(root, "MongoDatabase", "mongoDatabase"), defaults.mongoDatabase);
            return settings;
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read Ecotale config settings: " + e.getMessage());
            return defaults;
        }
    }

    @Nullable
    private Path findConfigPath() {
        List<Path> candidates = List.of(
                sourceDir.resolve("Ecotale.json"),
                sourceDir.resolve("ecotale.json"),
                sourceDir.resolve("EcotaleConfig.json"),
                sourceDir.resolve("config.json"),
                sourceDir.resolve("config").resolve("Ecotale.json"),
                sourceDir.resolve("config").resolve("ecotale.json"),
                sourceDir.resolve("config").resolve("config.json")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        try (var stream = Files.list(sourceDir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".json")) {
                    continue;
                }
                if (name.equals("balances.json")) {
                    continue;
                }
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root != null && (root.has("StorageProvider") || root.has("storageProvider"))) {
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
                              @Nullable Long balance,
                              @Nullable Long lastSeenAt) {
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

        if (lastSeenAt != null && lastSeenAt > 0L) {
            if (existing.lastSeenAt == null || lastSeenAt > existing.lastSeenAt) {
                existing.lastSeenAt = lastSeenAt;
                changed = true;
            }
        }
        return changed;
    }

    @Nullable
    private static JsonArray getArray(@Nonnull JsonObject obj, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.get(key).isJsonNull()) {
                    continue;
                }
                JsonElement element = obj.get(key);
                if (element.isJsonArray()) {
                    return element.getAsJsonArray();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static String getString(@Nonnull JsonObject obj, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.get(key).isJsonNull()) {
                    continue;
                }
                String value = obj.get(key).getAsString();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Integer getInteger(@Nonnull JsonObject obj, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.get(key).isJsonNull()) {
                    continue;
                }
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Long getLong(@Nonnull JsonObject obj, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.get(key).isJsonNull()) {
                    continue;
                }
                return obj.get(key).getAsLong();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Double getDouble(@Nonnull JsonObject obj, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.get(key).isJsonNull()) {
                    continue;
                }
                return obj.get(key).getAsDouble();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static String firstNonBlank(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
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

    @Nullable
    private static Long parsePositiveLong(@Nullable Long value) {
        if (value == null || value <= 0L) {
            return null;
        }
        return value;
    }

    private static int parsePositiveInt(@Nullable Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    @Nullable
    private static Double documentDouble(@Nonnull Document document, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                Object raw = document.get(key);
                if (raw == null) {
                    continue;
                }
                if (raw instanceof Number number) {
                    return number.doubleValue();
                }
                if (raw instanceof Decimal128 decimal128) {
                    return decimal128.bigDecimalValue().doubleValue();
                }
                if (raw instanceof String str && !str.isBlank()) {
                    return Double.parseDouble(str.trim());
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Long documentEpochMillis(@Nonnull Document document, @Nonnull String... keys) {
        for (String key : keys) {
            try {
                Object raw = document.get(key);
                if (raw == null) {
                    continue;
                }
                if (raw instanceof Date date) {
                    return date.getTime();
                }
                if (raw instanceof Number number) {
                    return number.longValue();
                }
                if (raw instanceof String str && !str.isBlank()) {
                    return Long.parseLong(str.trim());
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nonnull
    private static String blankToDefault(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    @Nonnull
    private static String sanitizeTablePrefix(@Nullable String prefix) {
        String raw = blankToDefault(prefix, DEFAULT_MYSQL_TABLE_PREFIX).trim();
        if (raw.isBlank()) {
            return DEFAULT_MYSQL_TABLE_PREFIX;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            if (!valid) {
                return DEFAULT_MYSQL_TABLE_PREFIX;
            }
        }
        return raw;
    }

    private static final class EcotaleSettings {
        String storageProvider = "h2";
        String mysqlHost = DEFAULT_MYSQL_HOST;
        int mysqlPort = DEFAULT_MYSQL_PORT;
        String mysqlDatabase = DEFAULT_MYSQL_DATABASE;
        String mysqlUsername = DEFAULT_MYSQL_USERNAME;
        String mysqlPassword = DEFAULT_MYSQL_PASSWORD;
        String mysqlTablePrefix = DEFAULT_MYSQL_TABLE_PREFIX;
        String mongoUri = DEFAULT_MONGO_URI;
        String mongoDatabase = DEFAULT_MONGO_DATABASE;
    }

    private static final class MutableUser {
        private final UUID uuid;
        private String name;
        private Long balance;
        private Long lastSeenAt;

        private MutableUser(@Nonnull UUID uuid) {
            this.uuid = Objects.requireNonNull(uuid, "uuid");
        }
    }
}
