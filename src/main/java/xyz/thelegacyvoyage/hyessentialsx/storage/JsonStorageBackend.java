package xyz.thelegacyvoyage.hyessentialsx.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JsonStorageBackend implements StorageBackend {

    private final Path dataFolder;
    private final Gson gson;

    public JsonStorageBackend(@Nonnull Path dataFolder) {
        this.dataFolder = dataFolder;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            Files.createDirectories(this.dataFolder);
            Files.createDirectories(playersFolder());
        } catch (Exception e) {
            Log.error("Failed to create data folders under: " + this.dataFolder, e);
        }
    }

    @Override
    @Nullable
    public PlayerDataModel loadPlayerData(@Nonnull UUID uuid) {
        Path file = playerFile(uuid);
        if (!Files.exists(file)) {
            return new PlayerDataModel();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            PlayerDataModel loaded = gson.fromJson(json, PlayerDataModel.class);
            return loaded != null ? loaded : new PlayerDataModel();
        } catch (Exception e) {
            Log.warn("Failed to load player data for " + uuid + ": " + e.getMessage());
            return new PlayerDataModel();
        }
    }

    @Override
    public void savePlayerData(@Nonnull UUID uuid, @Nonnull PlayerDataModel data) {
        try {
            Files.writeString(playerFile(uuid), gson.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.error("Failed to save player data for " + uuid + ": " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public Set<UUID> listPlayerIds() {
        Set<UUID> ids = new HashSet<>();
        try {
            if (!Files.exists(playersFolder())) return ids;
            Files.list(playersFolder()).forEach(path -> {
                String name = path.getFileName().toString();
                if (!name.endsWith(".json")) return;
                String raw = name.substring(0, name.length() - 5);
                try {
                    ids.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        return ids;
    }

    @Override
    @Nonnull
    public Map<String, WarpModel> loadWarps() {
        Path file = warpsFile();
        if (!Files.exists(file)) return new HashMap<>();
        try {
            Type type = new TypeToken<Map<String, WarpModel>>() {}.getType();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, WarpModel> loaded = gson.fromJson(json, type);
            return loaded != null ? new HashMap<>(loaded) : new HashMap<>();
        } catch (Exception e) {
            Log.warn("Failed to load warps.json: " + e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public void saveWarps(@Nonnull Map<String, WarpModel> warps) {
        try {
            Files.writeString(warpsFile(), gson.toJson(warps), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.error("Failed to save warps.json: " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public Map<String, KitModel> loadKits() {
        Path file = kitsFile();
        if (!Files.exists(file)) return new HashMap<>();
        try {
            Type type = new TypeToken<Map<String, KitModel>>() {}.getType();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, KitModel> loaded = gson.fromJson(json, type);
            return loaded != null ? new HashMap<>(loaded) : new HashMap<>();
        } catch (Exception e) {
            Log.warn("Failed to load kits.json: " + e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public void saveKits(@Nonnull Map<String, KitModel> kits) {
        try {
            Files.writeString(kitsFile(), gson.toJson(kits), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.error("Failed to save kits.json: " + e.getMessage());
        }
    }

    @Override
    @Nonnull
    public Map<String, ShopModel> loadShops() {
        Path file = shopsFile();
        if (!Files.exists(file)) return new HashMap<>();
        try {
            Type type = new TypeToken<Map<String, ShopModel>>() {}.getType();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, ShopModel> loaded = gson.fromJson(json, type);
            return loaded != null ? new HashMap<>(loaded) : new HashMap<>();
        } catch (Exception e) {
            Log.warn("Failed to load shops.json: " + e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public void saveShops(@Nonnull Map<String, ShopModel> shops) {
        try {
            Files.writeString(shopsFile(), gson.toJson(shops), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.error("Failed to save shops.json: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        // no-op
    }

    private Path playersFolder() {
        return dataFolder.resolve("players");
    }

    private Path playerFile(@Nonnull UUID uuid) {
        return playersFolder().resolve(uuid.toString() + ".json");
    }

    private Path warpsFile() {
        return dataFolder.resolve("warps.json");
    }

    private Path kitsFile() {
        return dataFolder.resolve("kits.json");
    }

    private Path shopsFile() {
        return dataFolder.resolve("shops.json");
    }

    
}
