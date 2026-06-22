package xyz.thelegacyvoyage.hyessentialsx.migration;

import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MigrationManager {

    private final StorageManager storage;
    private final SpawnManager spawnManager;
    private final Path dataFolder;

    public MigrationManager(@Nonnull StorageManager storage,
                            @Nonnull SpawnManager spawnManager,
                            @Nonnull Path dataFolder) {
        this.storage = storage;
        this.spawnManager = spawnManager;
        this.dataFolder = dataFolder;
    }

    @Nonnull
    public Path resolveSourceDir(@Nonnull ModType mod) {
        Path parent = dataFolder.getParent();
        if (parent == null) {
            parent = dataFolder;
        }
        Path primary = parent.resolve(mod.getFolderName());
        if (mod == ModType.EASY_HOME && (!Files.exists(primary) || !Files.isDirectory(primary))) {
            Path fallback = parent.resolve("EasyHome");
            if (Files.exists(fallback) && Files.isDirectory(fallback)) {
                return fallback;
            }
        }
        return primary;
    }

    @Nonnull
    public ModMigration.MigrationResult migrate(@Nonnull ModType mod, boolean merge) {
        Path sourceDir = resolveSourceDir(mod);
        ModMigration.MigrationResult result = new ModMigration.MigrationResult(mod.getDisplayName());

        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            result.setSuccess(false);
            result.setErrorMessage("Source directory does not exist: " + sourceDir.toAbsolutePath());
            return result;
        }

        ModMigration migration = createMigration(mod, sourceDir);
        if (migration == null) {
            result.setSuccess(false);
            result.setErrorMessage("Unsupported mod: " + mod.getDisplayName());
            return result;
        }

        try {
            List<WarpModel> warps = safeList(migration.migrateWarps());
            List<ModMigration.HomeEntry> homes = safeList(migration.migrateHomes());
            List<SpawnModel> spawns = safeList(migration.migrateSpawns());
            int kits = migration.migrateKits();
            List<ModMigration.UserEntry> users = safeList(migration.migrateUsers());

            result.setWarpsCount(warps.size());
            result.setHomesCount(homes.size());
            result.setSpawnsCount(spawns.size());
            result.setKitsCount(kits);
            result.setUsersCount(users.size());

            applyWarps(warps, merge);
            applyHomes(homes, merge);
            applySpawns(spawns, merge);
            applyUsers(users, merge);

            if (kits > 0) {
                result.addNotice("Kits were detected but are not migrated automatically.");
            }

            result.setSuccess(true);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            Log.warn("[HyEssentialsX] Migration failed: " + e.getMessage());
        }

        return result;
    }

    @Nonnull
    private static <T> List<T> safeList(@Nullable List<T> list) {
        return list == null ? List.of() : list;
    }

    @Nullable
    private ModMigration createMigration(@Nonnull ModType mod, @Nonnull Path sourceDir) {
        return switch (mod) {
            case HYSSENTIALS -> new HyssentialsMigration(sourceDir);
            case ELITE_ESSENTIALS -> new EliteEssentialsMigration(sourceDir);
            case ESSENTIALS_CORE -> new EssentialsCoreMigration(sourceDir);
            case ESSENTIALS_PLUS -> new EssentialsPlusMigration(sourceDir);
            case HOMES_PLUS -> new HomesPlusMigration(sourceDir);
            case HOME_MANAGER -> new HomeManagerMigration(sourceDir);
            case KUKSO_HY_WARPS -> new KuksoHyWarpsMigration(sourceDir);
            case EASY_HOME -> new EasyHomeMigration(sourceDir);
            case PLAYTIME -> new PlaytimeMigration(sourceDir);
        };
    }

    private void applyWarps(@Nonnull List<WarpModel> warps, boolean merge) {
        if (warps.isEmpty()) {
            return;
        }
        if (!merge) {
            clearWarps();
            Log.warn("[HyEssentialsX] Replacing existing warps (merge=false)");
        }
        int added = 0;
        int skipped = 0;
        for (WarpModel warp : warps) {
            if (warp == null) {
                continue;
            }
            String name = warp.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (merge && storage.getWarp(name) != null) {
                skipped++;
                continue;
            }
            storage.setWarp(name, warp);
            added++;
        }
        Log.info("[HyEssentialsX] Applied warps: " + added + " added, " + skipped + " skipped");
    }

    private void clearWarps() {
        for (String name : storage.getWarps().keySet()) {
            storage.deleteWarp(name);
        }
    }

    private void applyHomes(@Nonnull List<ModMigration.HomeEntry> homes, boolean merge) {
        if (homes.isEmpty()) {
            return;
        }
        if (!merge) {
            clearHomes();
            Log.warn("[HyEssentialsX] Replacing existing homes (merge=false)");
        }
        Map<UUID, PlayerDataModel> modified = new HashMap<>();
        int added = 0;
        int skipped = 0;
        for (ModMigration.HomeEntry entry : homes) {
            if (entry == null) {
                continue;
            }
            UUID playerId = entry.getPlayerId();
            HomeModel home = entry.getHome();
            if (playerId == null || home == null) {
                continue;
            }
            String name = home.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            PlayerDataModel data = modified.computeIfAbsent(playerId, storage::getPlayerData);
            Map<String, HomeModel> map = data.getHomes();
            String key = name.toLowerCase(Locale.ROOT);
            if (merge && map.containsKey(key)) {
                skipped++;
                continue;
            }
            map.put(key, home);
            added++;
        }
        for (Map.Entry<UUID, PlayerDataModel> entry : modified.entrySet()) {
            storage.savePlayerDataAsync(entry.getKey(), entry.getValue());
        }
        Log.info("[HyEssentialsX] Applied homes: " + added + " added, " + skipped + " skipped");
    }

    private void clearHomes() {
        for (UUID uuid : storage.listPlayerIds()) {
            PlayerDataModel data = storage.getPlayerData(uuid);
            if (!data.getHomes().isEmpty()) {
                data.getHomes().clear();
                storage.savePlayerDataAsync(uuid, data);
            }
        }
    }

    private void applySpawns(@Nonnull List<SpawnModel> spawns, boolean merge) {
        if (spawns.isEmpty()) {
            return;
        }
        if (!merge) {
            spawnManager.clearSpawn();
            Log.warn("[HyEssentialsX] Replacing existing spawn (merge=false)");
        }
        if (merge && spawnManager.hasSpawn()) {
            Log.warn("[HyEssentialsX] Spawn already exists - skipping migrated spawn");
            return;
        }
        SpawnModel spawn = spawns.get(0);
        if (spawn != null) {
            spawnManager.setSpawn(spawn);
            Log.info("[HyEssentialsX] Applied spawn: " + spawn.getWorldName());
        }
    }

    private void applyUsers(@Nonnull List<ModMigration.UserEntry> users, boolean merge) {
        if (users.isEmpty()) {
            return;
        }
        int added = 0;
        int updated = 0;
        java.util.Set<UUID> existingIds = storage.listPlayerIds();
        Map<UUID, PlayerDataModel> modified = new HashMap<>();
        for (ModMigration.UserEntry entry : users) {
            if (entry == null) {
                continue;
            }
            UUID uuid = entry.getPlayerId();
            if (uuid == null) {
                continue;
            }
            PlayerDataModel data = modified.computeIfAbsent(uuid, storage::getPlayerData);
            boolean exists = existingIds.contains(uuid);
            if (entry.getName() != null && !entry.getName().isBlank()) {
                storage.updatePlayerName(uuid, entry.getName().trim());
                data.setLastKnownName(entry.getName().trim());
            }
            Long balance = entry.getBalance();
            if (balance != null) {
                if (merge) {
                    long next = data.getBalance() + balance;
                    data.setBalance(next);
                } else {
                    data.setBalance(balance);
                }
            }
            Long lastSeen = entry.getLastSeenAt();
            if (lastSeen != null && lastSeen > 0) {
                data.setLastSeenAt(merge ? Math.max(data.getLastSeenAt(), lastSeen) : lastSeen);
            }
            Long lastJoin = entry.getLastJoinAt();
            if (lastJoin != null && lastJoin > 0) {
                data.setLastJoinAt(merge ? Math.max(data.getLastJoinAt(), lastJoin) : lastJoin);
            }
            Long playtime = entry.getPlaytimeSeconds();
            if (playtime != null && playtime > 0) {
                data.setPlaytimeSeconds(merge ? Math.max(data.getPlaytimeSeconds(), playtime) : playtime);
            }
            if (exists) {
                updated++;
            } else {
                added++;
            }
        }
        for (Map.Entry<UUID, PlayerDataModel> entry : modified.entrySet()) {
            storage.savePlayerDataAsync(entry.getKey(), entry.getValue());
        }
        Log.info("[HyEssentialsX] Applied users: " + added + " added, " + updated + " updated");
    }

    public enum ModType {
        HYSSENTIALS("Hyssentials", "com.leclowndu93150_Hyssentials"),
        ELITE_ESSENTIALS("EliteEssentials", "EliteEssentials"),
        ESSENTIALS_CORE("EssentialsCore", "com.nhulston_Essentials"),
        ESSENTIALS_PLUS("EssentialsPlus", "EssentialsPlus"),
        HOMES_PLUS("HomesPlus", "HomesPlus_HomesPlus"),
        HOME_MANAGER("HomeManager", "homemanager-data"),
        KUKSO_HY_WARPS("KuksoHyWarps", "KuksoHyWarps"),
        EASY_HOME("EasyHome", "cryptobench_EasyHome"),
        PLAYTIME("Playtime", "Playtime");

        private final String displayName;
        private final String folderName;

        ModType(@Nonnull String displayName, @Nonnull String folderName) {
            this.displayName = displayName;
            this.folderName = folderName;
        }

        @Nonnull
        public String getDisplayName() {
            return displayName;
        }

        @Nonnull
        public String getFolderName() {
            return folderName;
        }

        @Nullable
        public static ModType fromName(@Nullable String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            for (ModType mod : values()) {
                String display = mod.displayName.toLowerCase(Locale.ROOT);
                String folder = mod.folderName.toLowerCase(Locale.ROOT);
                if (display.equals(normalized) || folder.equals(normalized)) {
                    return mod;
                }
            }
            return null;
        }

        @Nonnull
        public static String supportedNames() {
            return java.util.Arrays.stream(values())
                    .map(ModType::getDisplayName)
                    .collect(Collectors.joining(", "));
        }
    }
}
