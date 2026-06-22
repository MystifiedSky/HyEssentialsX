package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EssentialsPlusMigration extends ModMigration {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public EssentialsPlusMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "EssentialsPlus");
    }

    @Override
    @Nonnull
    public List<WarpModel> migrateWarps() throws Exception {
        Path warpsFile = sourceDir.resolve("warps.json");
        if (!Files.exists(warpsFile)) {
            Log.warn("[HyEssentialsX] EssentialsPlus warps.json not found: " + warpsFile.toAbsolutePath());
            return List.of();
        }
        List<WarpModel> warps = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(warpsFile, StandardCharsets.UTF_8)) {
            WarpsFile file = GSON.fromJson(reader, WarpsFile.class);
            if (file == null || file.warps == null || file.warps.isEmpty()) {
                return warps;
            }
            for (WarpEntry entry : file.warps) {
                if (entry == null || entry.position == null) {
                    continue;
                }
                String name = entry.name;
                if (name == null || name.isBlank()) {
                    continue;
                }
                String worldName = resolveWorldName(null, entry.world);
                float yaw = entry.rotation != null ? (float) entry.rotation.y : 0f;
                float pitch = entry.rotation != null ? (float) entry.rotation.x : 0f;
                WarpModel warp = new WarpModel(
                        name,
                        worldName,
                        entry.position.x,
                        entry.position.y,
                        entry.position.z,
                        yaw,
                        pitch
                );
                warps.add(warp);
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read EssentialsPlus warps.json: " + e.getMessage());
            throw e;
        }
        Log.info("[HyEssentialsX] Migrated " + warps.size() + " warps from EssentialsPlus");
        return warps;
    }

    @Override
    @Nonnull
    public List<HomeEntry> migrateHomes() throws Exception {
        Path homesFile = sourceDir.resolve("homes.json");
        if (!Files.exists(homesFile)) {
            Log.warn("[HyEssentialsX] EssentialsPlus homes.json not found: " + homesFile.toAbsolutePath());
            return List.of();
        }
        List<HomeEntry> homes = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(homesFile, StandardCharsets.UTF_8)) {
            HomesFile file = GSON.fromJson(reader, HomesFile.class);
            if (file == null || file.homes == null || file.homes.isEmpty()) {
                return homes;
            }
            for (HomeEntryData entry : file.homes) {
                if (entry == null || entry.uuid == null || entry.position == null) {
                    continue;
                }
                UUID playerId = parseUuid(entry.uuid);
                if (playerId == null) {
                    continue;
                }
                String name = entry.name;
                if (name == null || name.isBlank()) {
                    continue;
                }
                String worldName = resolveWorldName(null, entry.world);
                float yaw = entry.rotation != null ? (float) entry.rotation.y : 0f;
                float pitch = entry.rotation != null ? (float) entry.rotation.x : 0f;
                HomeModel home = new HomeModel(
                        name,
                        null,
                        worldName,
                        entry.position.x,
                        entry.position.y,
                        entry.position.z,
                        yaw,
                        pitch
                );
                homes.add(new HomeEntry(playerId, home));
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read EssentialsPlus homes.json: " + e.getMessage());
            throw e;
        }
        Log.info("[HyEssentialsX] Migrated " + homes.size() + " homes from EssentialsPlus");
        return homes;
    }

    @Override
    @Nonnull
    public List<SpawnModel> migrateSpawns() throws Exception {
        Path spawnsFile = sourceDir.resolve("spawns.json");
        if (!Files.exists(spawnsFile)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(spawnsFile, StandardCharsets.UTF_8)) {
            SpawnsFile file = GSON.fromJson(reader, SpawnsFile.class);
            if (file == null || file.spawns == null || file.spawns.isEmpty()) {
                return List.of();
            }
            SpawnEntry selected = null;
            for (SpawnEntry entry : file.spawns) {
                if (entry == null) {
                    continue;
                }
                if (entry.mainSpawn) {
                    selected = entry;
                    break;
                }
                if (selected == null) {
                    selected = entry;
                }
            }
            if (selected == null || selected.position == null) {
                return List.of();
            }
            String worldName = resolveWorldName(null, selected.world);
            float yaw = selected.rotation != null ? (float) selected.rotation.y : 0f;
            float pitch = selected.rotation != null ? (float) selected.rotation.x : 0f;
            SpawnModel spawn = new SpawnModel(
                    worldName,
                    selected.position.x,
                    selected.position.y,
                    selected.position.z,
                    yaw,
                    pitch
            );
            return List.of(spawn);
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read EssentialsPlus spawns.json: " + e.getMessage());
            throw e;
        }
    }

    @Override
    @Nonnull
    public List<UserEntry> migrateUsers() throws Exception {
        Path usersDir = sourceDir.resolve("users");
        if (!Files.isDirectory(usersDir)) {
            Log.warn("[HyEssentialsX] EssentialsPlus users directory not found: " + usersDir.toAbsolutePath());
            return List.of();
        }
        File[] userFiles = usersDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (userFiles == null || userFiles.length == 0) {
            return List.of();
        }
        List<UserEntry> users = new ArrayList<>();
        for (File userFile : userFiles) {
            try (Reader reader = Files.newBufferedReader(userFile.toPath(), StandardCharsets.UTF_8)) {
                UserData data = GSON.fromJson(reader, UserData.class);
                if (data == null || data.uuid == null) {
                    continue;
                }
                UUID playerId = parseUuid(data.uuid);
                if (playerId == null) {
                    continue;
                }
                Long balance = data.balance != null ? Math.round(data.balance) : null;
                Long lastJoin = data.lastJoinTimestamp != null && data.lastJoinTimestamp > 0 ? data.lastJoinTimestamp : null;
                Long firstJoin = data.firstJoinTimestamp != null && data.firstJoinTimestamp > 0 ? data.firstJoinTimestamp : null;
                Long playtimeSeconds = null;
                if (data.playtime != null && data.playtime > 0) {
                    playtimeSeconds = Math.max(0L, data.playtime / 1000L);
                }
                Long lastSeen = lastJoin != null ? lastJoin : firstJoin;
                users.add(new UserEntry(
                        playerId,
                        data.username,
                        balance,
                        lastSeen,
                        lastJoin,
                        playtimeSeconds
                ));
            } catch (Exception e) {
                Log.warn("[HyEssentialsX] Failed to read EssentialsPlus user file " + userFile.getName() + ": " + e.getMessage());
            }
        }
        Log.info("[HyEssentialsX] Migrated " + users.size() + " users from EssentialsPlus");
        return users;
    }

    private static final class WarpsFile {
        String version;
        List<WarpEntry> warps;
    }

    private static final class WarpEntry {
        String name;
        Vector position;
        Vector rotation;
        String world;
    }

    private static final class HomesFile {
        String version;
        List<HomeEntryData> homes;
    }

    private static final class HomeEntryData {
        String uuid;
        String name;
        Vector position;
        Vector rotation;
        String world;
    }

    private static final class SpawnsFile {
        String version;
        List<SpawnEntry> spawns;
    }

    private static final class SpawnEntry {
        Vector position;
        Vector rotation;
        String world;
        boolean mainSpawn;
    }

    private static final class Vector {
        double x;
        double y;
        double z;
    }

    private static final class UserData {
        String uuid;
        String username;
        Long firstJoinTimestamp;
        Long lastJoinTimestamp;
        Long playtime;
        Double balance;
    }
}
