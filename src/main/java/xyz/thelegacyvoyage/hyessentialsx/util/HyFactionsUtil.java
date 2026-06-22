package xyz.thelegacyvoyage.hyessentialsx.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HyFactionsUtil {

    private static final Gson GSON = new Gson();
    private static final Path[] BASE_DIR_CANDIDATES = new Path[]{
            Path.of("mods", "Kaws_Hyfaction", "config"),
            Path.of("mods", "Hyfaction", "config"),
            Path.of("plugins", "Hyfaction", "config")
    };

    private static final Object LOCK = new Object();
    private static volatile Map<UUID, String> cachedPlayerFactions = Map.of();
    private static volatile long nameCacheLastModified = -1L;
    private static volatile long factionFilesLastModified = -1L;
    @Nullable
    private static volatile Path baseDir;

    private HyFactionsUtil() {}

    @Nullable
    public static String getFactionName(@Nonnull UUID playerId) {
        ensureCachesLoaded();
        String name = cachedPlayerFactions.get(playerId);
        return (name == null || name.isBlank()) ? null : name;
    }

    private static void ensureCachesLoaded() {
        synchronized (LOCK) {
            resolveBaseDir();
            loadNameCacheIfNeeded();
            loadFactionFilesIfNeeded();
        }
    }

    private static void loadNameCacheIfNeeded() {
        try {
            Path nameCache = resolveNameCachePath();
            if (nameCache == null || !Files.exists(nameCache)) {
                cachedPlayerFactions = Map.of();
                nameCacheLastModified = -1L;
                return;
            }
            long lastModified = Files.getLastModifiedTime(nameCache).toMillis();
            if (lastModified == nameCacheLastModified && !cachedPlayerFactions.isEmpty()) {
                return;
            }
            String json = Files.readString(nameCache, StandardCharsets.UTF_8);
            JsonElement root = GSON.fromJson(json, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                cachedPlayerFactions = Map.of();
                nameCacheLastModified = lastModified;
                return;
            }
            Map<UUID, String> map = new HashMap<>();
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("Values") && obj.get("Values").isJsonArray()) {
                for (JsonElement element : obj.getAsJsonArray("Values")) {
                    if (!element.isJsonObject()) continue;
                    JsonObject entry = element.getAsJsonObject();
                    String id = firstNonBlank(str(entry, "PlayerUUID"), str(entry, "PlayerId"), str(entry, "Uuid"));
                    UUID playerId = id != null ? parseUuid(id) : null;
                    if (playerId == null) continue;
                    String factionName = firstNonBlank(
                            str(entry, "FactionName"),
                            str(entry, "Faction"),
                            str(entry, "Name")
                    );
                    if (factionName != null && !factionName.isBlank()) {
                        map.put(playerId, factionName);
                    }
                }
            } else {
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    UUID playerId = parseUuid(entry.getKey());
                    if (playerId == null) continue;
                    String factionName = extractFactionName(entry.getValue());
                    if (factionName != null && !factionName.isBlank()) {
                        map.put(playerId, factionName);
                    }
                }
            }
            cachedPlayerFactions = map;
            nameCacheLastModified = lastModified;
        } catch (Exception e) {
            cachedPlayerFactions = Map.of();
        }
    }

    private static void loadFactionFilesIfNeeded() {
        try {
            Path factionDir = resolveFactionDir();
            if (factionDir == null || !Files.exists(factionDir)) {
                factionFilesLastModified = -1L;
                return;
            }
            long lastModified = latestModifiedTime(factionDir);
            if (lastModified == factionFilesLastModified) {
                return;
            }
            Map<UUID, String> playerMap = new HashMap<>(cachedPlayerFactions);
            try (var stream = Files.list(factionDir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> readFactionMembers(path, playerMap));
            }
            cachedPlayerFactions = playerMap;
            factionFilesLastModified = lastModified;
        } catch (Exception e) {
            factionFilesLastModified = -1L;
        }
    }

    @Nullable
    private static String extractFactionName(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (looksLikeUuid(value)) {
                UUID factionId = parseUuid(value);
                return factionId != null ? null : null;
            }
            return value;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String direct = firstNonBlank(
                    str(obj, "faction"),
                    str(obj, "factionName"),
                    str(obj, "name"),
                    str(obj, "tag"),
                    str(obj, "displayName")
            );
            if (direct != null) return direct;
            String id = firstNonBlank(str(obj, "factionId"), str(obj, "id"), str(obj, "uuid"));
            if (id != null && looksLikeUuid(id)) return null;
        }
        return null;
    }

    private static void readFactionMembers(@Nonnull Path path, @Nonnull Map<UUID, String> playerMap) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement root = GSON.fromJson(json, JsonElement.class);
            if (root == null || !root.isJsonObject()) return;
            JsonObject obj = root.getAsJsonObject();
            String factionName = firstNonBlank(
                    str(obj, "Name"),
                    str(obj, "name"),
                    str(obj, "FactionName"),
                    str(obj, "displayName"),
                    str(obj, "tag")
            );
            if (factionName == null) return;
            addMemberId(playerMap, str(obj, "Owner"), factionName);
            JsonElement members = obj.get("Members");
            if (members != null && members.isJsonArray()) {
                for (JsonElement entry : members.getAsJsonArray()) {
                    if (!entry.isJsonPrimitive()) continue;
                    addMemberId(playerMap, entry.getAsString(), factionName);
                }
            }
        } catch (Exception e) {
            return;
        }
    }

    @Nullable
    private static String str(@Nonnull JsonObject obj, @Nonnull String key) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return null;
        String value = el.getAsString();
        return value.isBlank() ? null : value;
    }

    @Nullable
    private static UUID parseUuid(@Nonnull String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean looksLikeUuid(@Nonnull String value) {
        return value.length() >= 32 && value.length() <= 36;
    }

    @Nullable
    private static String firstNonBlank(@Nullable String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    @Nonnull
    private static String stripExtension(@Nonnull String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void resolveBaseDir() {
        if (baseDir != null) return;
        for (Path candidate : BASE_DIR_CANDIDATES) {
            if (Files.exists(candidate)) {
                baseDir = candidate;
                return;
            }
        }
    }

    @Nullable
    private static Path resolveNameCachePath() {
        if (baseDir == null) return null;
        return baseDir.resolve("NameCache.json");
    }

    @Nullable
    private static Path resolveFactionDir() {
        if (baseDir == null) return null;
        return baseDir.resolve("faction");
    }

    private static long latestModifiedTime(@Nonnull Path dir) {
        try (var stream = Files.list(dir)) {
            long latest = Files.getLastModifiedTime(dir).toMillis();
            for (Path path : (Iterable<Path>) stream::iterator) {
                long time = Files.getLastModifiedTime(path).toMillis();
                if (time > latest) latest = time;
            }
            return latest;
        } catch (Exception e) {
            return -1L;
        }
    }

    private static void addMemberId(@Nonnull Map<UUID, String> map, @Nullable String id, @Nonnull String factionName) {
        if (id == null || id.isBlank()) return;
        UUID uuid = parseUuid(id);
        if (uuid != null) {
            map.put(uuid, factionName);
        }
    }
}
