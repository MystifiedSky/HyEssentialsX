package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
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

public final class EliteEssentialsMigration extends ModMigration {

    private static final Gson GSON = new GsonBuilder().create();

    public EliteEssentialsMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "EliteEssentials");
    }

    @Override
    @Nonnull
    public List<WarpModel> migrateWarps() throws Exception {
        Path warpsFile = sourceDir.resolve("warps.json");
        if (!Files.exists(warpsFile)) {
            Log.warn("[HyEssentialsX] EliteEssentials warps.json not found: " + warpsFile.toAbsolutePath());
            return List.of();
        }
        List<WarpModel> warps = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(warpsFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, EliteWarpData>>() {}.getType();
            Map<String, EliteWarpData> data = GSON.fromJson(reader, type);
            if (data == null || data.isEmpty()) {
                Log.warn("[HyEssentialsX] EliteEssentials warps.json is empty");
                return warps;
            }
            for (Map.Entry<String, EliteWarpData> entry : data.entrySet()) {
                String name = entry.getKey();
                EliteWarpData warpData = entry.getValue();
                if (warpData == null || warpData.location == null) {
                    continue;
                }
                String worldName = resolveWorldName(warpData.location.world, null);
                WarpModel warp = new WarpModel(
                        name,
                        worldName,
                        warpData.location.x,
                        warpData.location.y,
                        warpData.location.z,
                        warpData.location.yaw,
                        warpData.location.pitch
                );
                warps.add(warp);
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read EliteEssentials warps.json: " + e.getMessage());
            throw e;
        }
        Log.info("[HyEssentialsX] Migrated " + warps.size() + " warps from EliteEssentials");
        return warps;
    }

    @Override
    @Nonnull
    public List<HomeEntry> migrateHomes() throws Exception {
        Path playersDir = sourceDir.resolve("players");
        if (!Files.isDirectory(playersDir)) {
            Log.warn("[HyEssentialsX] EliteEssentials players directory not found: " + playersDir.toAbsolutePath());
            return List.of();
        }
        File[] playerFiles = playersDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (playerFiles == null || playerFiles.length == 0) {
            Log.warn("[HyEssentialsX] EliteEssentials players directory is empty");
            return List.of();
        }
        List<HomeEntry> homes = new ArrayList<>();
        for (File playerFile : playerFiles) {
            try (Reader reader = Files.newBufferedReader(playerFile.toPath(), StandardCharsets.UTF_8)) {
                ElitePlayerData data = GSON.fromJson(reader, ElitePlayerData.class);
                if (data == null || data.uuid == null || data.homes == null || data.homes.isEmpty()) {
                    continue;
                }
                UUID playerId = parseUuid(data.uuid);
                if (playerId == null) {
                    continue;
                }
                for (Map.Entry<String, EliteHomeData> entry : data.homes.entrySet()) {
                    EliteHomeData homeData = entry.getValue();
                    if (homeData == null || homeData.location == null) {
                        continue;
                    }
                    String homeName = entry.getKey();
                    String worldName = resolveWorldName(homeData.location.world, null);
                    HomeModel home = new HomeModel(
                            homeName,
                            null,
                            worldName,
                            homeData.location.x,
                            homeData.location.y,
                            homeData.location.z,
                            homeData.location.yaw,
                            homeData.location.pitch
                    );
                    homes.add(new HomeEntry(playerId, home));
                }
            } catch (Exception e) {
                Log.warn("[HyEssentialsX] Failed to read EliteEssentials player file " + playerFile.getName() + ": " + e.getMessage());
            }
        }
        Log.info("[HyEssentialsX] Migrated " + homes.size() + " homes from EliteEssentials");
        return homes;
    }

    @Override
    public int migrateKits() throws Exception {
        Path kitsFile = sourceDir.resolve("kits.json");
        if (!Files.exists(kitsFile)) {
            return 0;
        }
        try (Reader reader = Files.newBufferedReader(kitsFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<EliteKitData>>() {}.getType();
            List<EliteKitData> kits = GSON.fromJson(reader, type);
            if (kits == null || kits.isEmpty()) {
                return 0;
            }
            Log.warn("[HyEssentialsX] EliteEssentials kits detected (" + kits.size() + "). Manual migration required.");
            return kits.size();
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read EliteEssentials kits.json: " + e.getMessage());
            throw e;
        }
    }

    @Override
    @Nonnull
    public List<UserEntry> migrateUsers() throws Exception {
        Path playersDir = sourceDir.resolve("players");
        if (!Files.isDirectory(playersDir)) {
            return List.of();
        }
        File[] playerFiles = playersDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (playerFiles == null || playerFiles.length == 0) {
            return List.of();
        }
        List<UserEntry> users = new ArrayList<>();
        for (File playerFile : playerFiles) {
            try (Reader reader = Files.newBufferedReader(playerFile.toPath(), StandardCharsets.UTF_8)) {
                ElitePlayerData data = GSON.fromJson(reader, ElitePlayerData.class);
                if (data == null || data.uuid == null) {
                    continue;
                }
                UUID playerId = parseUuid(data.uuid);
                if (playerId == null) {
                    continue;
                }
                Long balance = Math.round(data.wallet);
                Long lastSeen = data.lastSeen > 0 ? data.lastSeen : null;
                Long playtime = data.playTime > 0 ? data.playTime : null;
                users.add(new UserEntry(
                        playerId,
                        data.name,
                        balance,
                        lastSeen,
                        lastSeen,
                        playtime
                ));
            } catch (Exception e) {
                Log.warn("[HyEssentialsX] Failed to read EliteEssentials player file " + playerFile.getName() + ": " + e.getMessage());
            }
        }
        Log.info("[HyEssentialsX] Migrated " + users.size() + " users from EliteEssentials");
        return users;
    }

    private static final class EliteWarpData {
        LocationData location;
    }

    private static final class LocationData {
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }

    private static final class ElitePlayerData {
        String uuid;
        String name;
        long firstJoin;
        long lastSeen;
        long playTime;
        double wallet;
        Map<String, EliteHomeData> homes;
    }

    private static final class EliteHomeData {
        LocationData location;
    }

    private static final class EliteKitData {
        String id;
        String displayName;
    }
}
