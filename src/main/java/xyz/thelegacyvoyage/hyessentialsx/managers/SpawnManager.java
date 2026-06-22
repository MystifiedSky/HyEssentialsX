package xyz.thelegacyvoyage.hyessentialsx.managers;

import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class SpawnManager {

    private static final float RESPAWN_TELEPORT_DELAY_SECONDS = 0.25f;
    private static final String NAMED_SPAWN_PERMISSION_PREFIX = "hyessentialsx.spawn.";
    private static final Pattern NAMED_SPAWN_NAME_PATTERN = Pattern.compile("^[a-z0-9_-]{1,32}$");

    private final ConfigManager config;
    private final Map<UUID, PendingRespawnTeleport> pendingRespawnTeleports = new ConcurrentHashMap<>();
    public SpawnManager(@Nonnull ConfigManager config) {
        this.config = config;
    }

    public void setSpawn(@Nonnull SpawnModel point) {
        config.setSpawn(point);
        World world = Universe.get().getWorld(point.getWorldName());
        if (world != null) {
            applySpawnToWorld(world, point);
        }
    }

    public void setNamedSpawn(@Nonnull String name, @Nonnull SpawnModel point) {
        String key = normalizeSpawnName(name);
        if (key == null) {
            throw new IllegalArgumentException("Invalid named spawn: " + name);
        }
        config.setNamedSpawn(key, point);
    }

    public void clearSpawn() {
        config.clearSpawn();
    }

    public boolean clearNamedSpawn(@Nonnull String name) {
        String key = normalizeSpawnName(name);
        return key != null && config.clearNamedSpawn(key);
    }

    @Nullable
    public SpawnModel getSpawn() {
        return config.getSpawn();
    }

    @Nullable
    public SpawnModel getNamedSpawn(@Nonnull String name) {
        String key = normalizeSpawnName(name);
        return key == null ? null : config.getNamedSpawn(key);
    }

    public boolean hasSpawn() {
        return config.hasSpawn();
    }

    public boolean hasNamedSpawn(@Nonnull String name) {
        String key = normalizeSpawnName(name);
        return key != null && config.hasNamedSpawn(key);
    }

    @Nullable
    public SpawnModel getConfiguredSpawnForPlayer(@Nonnull PlayerRef player) {
        return getConfiguredSpawnForPlayer(player.getUuid());
    }

    @Nullable
    public SpawnModel getConfiguredSpawnForPlayer(@Nonnull UUID playerId) {
        SpawnModel permissionSpawn = getPermissionSpawn(playerId);
        if (permissionSpawn != null) {
            return permissionSpawn;
        }
        return getSpawn();
    }

    @Nullable
    public SpawnModel getSpawnForPlayer(@Nonnull World world, @Nonnull PlayerRef player) {
        return getSpawnForPlayer(world, player.getUuid());
    }

    @Nullable
    public SpawnModel getSpawnForPlayer(@Nonnull World world, @Nonnull UUID playerId) {
        SpawnModel configuredSpawn = getConfiguredSpawnForPlayer(playerId);
        if (configuredSpawn != null) {
            return configuredSpawn;
        }
        if (!config.isUseWorldDefaultSpawnIfUnset()) {
            return null;
        }
        return getWorldDefaultSpawn(world, playerId);
    }

    public void queueRespawnTeleport(@Nonnull UUID playerId, @Nonnull SpawnModel spawn) {
        pendingRespawnTeleports.put(playerId, new PendingRespawnTeleport(spawn));
    }

    @Nullable
    public SpawnModel peekRespawnTeleport(@Nonnull UUID playerId) {
        PendingRespawnTeleport pending = pendingRespawnTeleports.get(playerId);
        return pending != null ? pending.spawn : null;
    }

    public boolean tickRespawnTeleport(@Nonnull UUID playerId, float deltaSeconds) {
        PendingRespawnTeleport pending = pendingRespawnTeleports.get(playerId);
        if (pending == null) return false;
        pending.elapsedSeconds += deltaSeconds;
        return pending.elapsedSeconds >= RESPAWN_TELEPORT_DELAY_SECONDS;
    }

    public void resetRespawnTeleportDelay(@Nonnull UUID playerId) {
        PendingRespawnTeleport pending = pendingRespawnTeleports.get(playerId);
        if (pending != null) {
            pending.elapsedSeconds = 0f;
        }
    }

    public void clearRespawnTeleport(@Nonnull UUID playerId) {
        pendingRespawnTeleports.remove(playerId);
    }

    public void ensureDefaultSpawnInitialized(@Nonnull World world) {
        if (hasSpawn()) return;


        Vector3d pos = resolveWorldSpawnPosition(world);
        Rotation3f rot = resolveWorldSpawnRotation(world);

        if (pos == null) {
            Log.warn("Could not resolve default spawn position from World API. Spawn not initialized.");
            return;
        }

        float yaw = (rot != null) ? rot.y() : 0f;
        float pitch = (rot != null) ? rot.x() : 0f;

        setSpawn(new SpawnModel(
                world.getName(),
                pos.x(), pos.y(), pos.z(),
                yaw, pitch
        ));

        Log.info("Initialized default spawn from world: " + world.getName());
    }

    @Nullable
    public SpawnModel getSpawnOrWorldDefault(@Nonnull World world, @Nonnull UUID playerId) {
        SpawnModel spawn = getSpawn();
        if (spawn != null) return spawn;

        return getWorldDefaultSpawn(world, playerId);
    }

    @Nullable
    public SpawnModel getWorldDefaultSpawn(@Nonnull World world, @Nonnull UUID playerId) {
        ISpawnProvider provider = world.getWorldConfig().getSpawnProvider();
        if (provider == null) return null;

        Transform transform = provider.getSpawnPoint(world, playerId);
        if (transform == null) return null;

        Vector3d pos = transform.getPosition();
        com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
        if (pos == null) return null;

        float yaw = (rot != null) ? rot.y() : 0f;
        float pitch = (rot != null) ? rot.x() : 0f;

        return new SpawnModel(
                world.getName(),
                pos.x(), pos.y(), pos.z(),
                yaw, pitch
        );
    }

    public void syncWorldSpawnProvider() {
        SpawnModel spawn = getSpawn();
        if (spawn == null) return;

        World world = Universe.get().getWorld(spawn.getWorldName());
        if (world == null) {
            Log.warn("Spawn sync failed: world not found: " + spawn.getWorldName());
            return;
        }

        applySpawnToWorld(world, spawn);
    }

    // ---------- helpers ----------

    public static boolean isValidSpawnName(@Nullable String name) {
        return normalizeSpawnName(name) != null;
    }

    @Nullable
    public static String normalizeSpawnName(@Nullable String name) {
        if (name == null) return null;
        String value = name.trim().toLowerCase(Locale.ROOT);
        if (!NAMED_SPAWN_NAME_PATTERN.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    @Nullable
    private SpawnModel getPermissionSpawn(@Nonnull UUID playerId) {
        Map<String, SpawnModel> namedSpawns = config.getNamedSpawns();
        if (namedSpawns.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, SpawnModel> entry : namedSpawns.entrySet()) {
            String permission = NAMED_SPAWN_PERMISSION_PREFIX + entry.getKey();
            if (PermissionsModule.get().hasPermission(playerId, permission)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Nullable
    private World firstLoadedWorld() {
        try {
            Object universe = Universe.get();
            Method m = universe.getClass().getMethod("getWorlds");
            Object result = m.invoke(universe);

            if (result instanceof Collection<?> worlds && !worlds.isEmpty()) {
                Object first = worlds.iterator().next();
                if (first instanceof World w) return w;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    private Vector3d resolveWorldSpawnPosition(@Nonnull World world) {
        String[] candidates = {
                "getSpawnPosition",
                "getDefaultSpawnPosition",
                "getSpawnPoint",
                "getDefaultSpawnPoint",
                "getSpawnLocation",
                "getDefaultSpawnLocation"
        };

        for (String name : candidates) {
            Object out = tryInvokeNoArgs(world, name);
            if (out instanceof Vector3d v) return v;
        }
        return null;
    }

    @Nullable
    private Rotation3f resolveWorldSpawnRotation(@Nonnull World world) {
        String[] candidates = {
                "getSpawnRotation",
                "getDefaultSpawnRotation",
                "getSpawnOrientation",
                "getDefaultSpawnOrientation"
        };

        for (String name : candidates) {
            Object out = tryInvokeNoArgs(world, name);
            if (out instanceof Rotation3f v) return v;
            if (out instanceof Vector3f v) return new Rotation3f(v.x(), v.y(), v.z());
        }
        return null;
    }

    @Nullable
    private Object tryInvokeNoArgs(@Nonnull Object target, @Nonnull String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void applySpawnToWorld(@Nonnull World world, @Nonnull SpawnModel spawn) {
        try {
            Vector3d pos = new Vector3d(spawn.x(), spawn.y(), spawn.z());
            Rotation3f rot = new Rotation3f(spawn.pitch(), spawn.yaw(), 0f);
            Transform transform = new Transform(pos, rot);
            WorldConfig config = world.getWorldConfig();
            config.setSpawnProvider(new GlobalSpawnProvider(transform));
            config.markChanged();
            Log.info("Set spawn provider to custom spawn for world: " + world.getName());
        } catch (Throwable t) {
            Log.warn("Failed to apply custom spawn provider: " + t.getMessage());
        }
    }

    private static final class PendingRespawnTeleport {
        private final SpawnModel spawn;
        private float elapsedSeconds;

        private PendingRespawnTeleport(@Nonnull SpawnModel spawn) {
            this.spawn = spawn;
        }
    }
}

