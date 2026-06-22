package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public abstract class ModMigration {

    protected final Path sourceDir;
    protected final String modName;

    protected ModMigration(@Nonnull Path sourceDir, @Nonnull String modName) {
        this.sourceDir = Objects.requireNonNull(sourceDir, "sourceDir");
        this.modName = Objects.requireNonNull(modName, "modName");
    }

    @Nonnull
    public final Path getSourceDir() {
        return sourceDir;
    }

    @Nonnull
    public final String getModName() {
        return modName;
    }

    @Nonnull
    public abstract List<WarpModel> migrateWarps() throws Exception;

    @Nonnull
    public abstract List<HomeEntry> migrateHomes() throws Exception;

    @Nonnull
    public List<SpawnModel> migrateSpawns() throws Exception {
        return Collections.emptyList();
    }

    public int migrateKits() throws Exception {
        return 0;
    }

    @Nonnull
    public List<UserEntry> migrateUsers() throws Exception {
        return Collections.emptyList();
    }

    @Nonnull
    protected String resolveWorldName(@Nullable String worldName, @Nullable String worldUuid) {
        String direct = normalize(worldName);
        if (direct != null) {
            return direct;
        }
        UUID uuid = parseUuid(worldUuid);
        if (uuid != null) {
            String resolved = resolveWorldNameByUuid(uuid);
            if (resolved != null) {
                return resolved;
            }
        }
        return defaultWorldName();
    }

    @Nullable
    protected String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    @Nullable
    protected UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    protected String resolveWorldNameByUuid(@Nonnull UUID uuid) {
        try {
            Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds == null || worlds.isEmpty()) {
                return null;
            }
            for (World world : worlds.values()) {
                if (world == null || world.getWorldConfig() == null) {
                    continue;
                }
                UUID worldId = world.getWorldConfig().getUuid();
                if (uuid.equals(worldId)) {
                    return world.getName();
                }
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] World lookup failed: " + e.getMessage());
        }
        return null;
    }

    @Nonnull
    protected String defaultWorldName() {
        try {
            Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds != null && !worlds.isEmpty()) {
                for (World world : worlds.values()) {
                    if (world != null && world.getName() != null && !world.getName().isBlank()) {
                        return world.getName();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "default";
    }

    public static final class HomeEntry {
        private final UUID playerId;
        private final HomeModel home;

        public HomeEntry(@Nonnull UUID playerId, @Nonnull HomeModel home) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.home = Objects.requireNonNull(home, "home");
        }

        @Nonnull
        public UUID getPlayerId() {
            return playerId;
        }

        @Nonnull
        public HomeModel getHome() {
            return home;
        }
    }

    public static final class UserEntry {
        private final UUID playerId;
        private final String name;
        private final Long balance;
        private final Long lastSeenAt;
        private final Long lastJoinAt;
        private final Long playtimeSeconds;

        public UserEntry(@Nonnull UUID playerId,
                         @Nullable String name,
                         @Nullable Long balance,
                         @Nullable Long lastSeenAt,
                         @Nullable Long lastJoinAt,
                         @Nullable Long playtimeSeconds) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.name = name;
            this.balance = balance;
            this.lastSeenAt = lastSeenAt;
            this.lastJoinAt = lastJoinAt;
            this.playtimeSeconds = playtimeSeconds;
        }

        @Nonnull
        public UUID getPlayerId() {
            return playerId;
        }

        @Nullable
        public String getName() {
            return name;
        }

        @Nullable
        public Long getBalance() {
            return balance;
        }

        @Nullable
        public Long getLastSeenAt() {
            return lastSeenAt;
        }

        @Nullable
        public Long getLastJoinAt() {
            return lastJoinAt;
        }

        @Nullable
        public Long getPlaytimeSeconds() {
            return playtimeSeconds;
        }
    }

    public static final class MigrationResult {
        private final String modName;
        private boolean success;
        private int warpsCount;
        private int homesCount;
        private int spawnsCount;
        private int kitsCount;
        private int usersCount;
        private String errorMessage;
        private final List<String> notices = new ArrayList<>();

        public MigrationResult(@Nonnull String modName) {
            this.modName = modName;
        }

        @Nonnull
        public String getSummary() {
            if (!success) {
                return "[" + modName + " Migration] Failed: " + (errorMessage == null ? "Unknown error" : errorMessage);
            }
            return String.format("[%s Migration] Success - Warps: %d, Homes: %d, Spawns: %d, Kits: %d, Users: %d",
                    modName, warpsCount, homesCount, spawnsCount, kitsCount, usersCount);
        }

        @Nonnull
        public String getModName() {
            return modName;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getWarpsCount() {
            return warpsCount;
        }

        public void setWarpsCount(int warpsCount) {
            this.warpsCount = warpsCount;
        }

        public int getHomesCount() {
            return homesCount;
        }

        public void setHomesCount(int homesCount) {
            this.homesCount = homesCount;
        }

        public int getSpawnsCount() {
            return spawnsCount;
        }

        public void setSpawnsCount(int spawnsCount) {
            this.spawnsCount = spawnsCount;
        }

        public int getKitsCount() {
            return kitsCount;
        }

        public void setKitsCount(int kitsCount) {
            this.kitsCount = kitsCount;
        }

        public int getUsersCount() {
            return usersCount;
        }

        public void setUsersCount(int usersCount) {
            this.usersCount = usersCount;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(@Nullable String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public void addNotice(@Nonnull String notice) {
            String trimmed = notice.trim();
            if (!trimmed.isBlank()) {
                notices.add(trimmed);
            }
        }

        @Nonnull
        public List<String> getNotices() {
            return Collections.unmodifiableList(notices);
        }
    }
}
