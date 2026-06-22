package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EssentialsCoreMigration extends ModMigration {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public EssentialsCoreMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "EssentialsCore");
    }

    @Override
    @Nonnull
    public List<WarpModel> migrateWarps() throws Exception {
        Path warpsFile = sourceDir.resolve("warps.json");
        if (!Files.exists(warpsFile)) {
            Log.warn("[HyEssentialsX] EssentialsCore warps.json not found");
            return List.of();
        }
        List<WarpModel> warps = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(warpsFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, EssentialsCoreLocationData>>() {}.getType();
            Map<String, EssentialsCoreLocationData> data = GSON.fromJson(reader, type);
            if (data == null || data.isEmpty()) {
                return warps;
            }
            for (Map.Entry<String, EssentialsCoreLocationData> entry : data.entrySet()) {
                EssentialsCoreLocationData loc = entry.getValue();
                if (loc == null) {
                    continue;
                }
                String worldName = resolveWorldName(loc.world, null);
                WarpModel warp = new WarpModel(
                        entry.getKey(),
                        worldName,
                        loc.x,
                        loc.y,
                        loc.z,
                        loc.yaw,
                        loc.pitch
                );
                warps.add(warp);
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read EssentialsCore warps.json: " + e.getMessage());
            throw e;
        }
        Log.info("[HyEssentialsX] Migrated " + warps.size() + " warps from EssentialsCore");
        return warps;
    }

    @Override
    @Nonnull
    public List<HomeEntry> migrateHomes() throws Exception {
        Path playersDir = sourceDir.resolve("players");
        if (!Files.isDirectory(playersDir)) {
            Log.warn("[HyEssentialsX] EssentialsCore players directory not found");
            return List.of();
        }
        File[] playerFiles = playersDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (playerFiles == null || playerFiles.length == 0) {
            return List.of();
        }
        List<HomeEntry> homes = new ArrayList<>();
        for (File playerFile : playerFiles) {
            String fileName = playerFile.getName();
            String uuidString = fileName.substring(0, fileName.length() - 5);
            UUID playerId = parseUuid(uuidString);
            if (playerId == null) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(playerFile.toPath(), StandardCharsets.UTF_8)) {
                EssentialsCorePlayerData data = GSON.fromJson(reader, EssentialsCorePlayerData.class);
                if (data == null || data.homes == null) {
                    continue;
                }
                for (Map.Entry<String, EssentialsCoreLocationData> entry : data.homes.entrySet()) {
                    EssentialsCoreLocationData loc = entry.getValue();
                    if (loc == null) {
                        continue;
                    }
                    String worldName = resolveWorldName(loc.world, null);
                    HomeModel home = new HomeModel(
                            entry.getKey(),
                            null,
                            worldName,
                            loc.x,
                            loc.y,
                            loc.z,
                            loc.yaw,
                            loc.pitch
                    );
                    homes.add(new HomeEntry(playerId, home));
                }
            } catch (Exception e) {
                Log.warn("[HyEssentialsX] Failed to read EssentialsCore player file " + playerFile.getName() + ": " + e.getMessage());
            }
        }
        Log.info("[HyEssentialsX] Migrated " + homes.size() + " homes from EssentialsCore");
        return homes;
    }

    @Override
    @Nonnull
    public List<SpawnModel> migrateSpawns() throws Exception {
        Path spawnFile = sourceDir.resolve("spawn.json");
        if (!Files.exists(spawnFile)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(spawnFile, StandardCharsets.UTF_8)) {
            EssentialsCoreLocationData loc = GSON.fromJson(reader, EssentialsCoreLocationData.class);
            if (loc == null) {
                return List.of();
            }
            String worldName = resolveWorldName(loc.world, null);
            SpawnModel spawn = new SpawnModel(
                    worldName,
                    loc.x,
                    loc.y,
                    loc.z,
                    loc.yaw,
                    loc.pitch
            );
            return List.of(spawn);
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read EssentialsCore spawn.json: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public int migrateKits() throws Exception {
        Path kitsFile = sourceDir.resolve("kits.toml");
        if (!Files.exists(kitsFile)) {
            return 0;
        }
        Log.warn("[HyEssentialsX] EssentialsCore kits.toml detected. Manual migration required.");
        return 0;
    }

    private static final class EssentialsCoreLocationData {
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }

    private static final class EssentialsCorePlayerData {
        Map<String, EssentialsCoreLocationData> homes;
    }
}
