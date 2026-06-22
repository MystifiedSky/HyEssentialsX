package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnManager {

    private static final float RESPAWN_TELEPORT_DELAY_SECONDS = 0.25f;

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

    public void clearSpawn() {
        config.clearSpawn();
    }

    @Nullable
    public SpawnModel getSpawn() {
        return config.getSpawn();
    }

    public boolean hasSpawn() {
        return config.hasSpawn();
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
        Vector3f rot = resolveWorldSpawnRotation(world);

        if (pos == null) {
            Log.warn("Could not resolve default spawn position from World API. Spawn not initialized.");
            return;
        }

        float yaw = (rot != null) ? rot.getY() : 0f;
        float pitch = (rot != null) ? rot.getX() : 0f;

        setSpawn(new SpawnModel(
                world.getName(),
                pos.getX(), pos.getY(), pos.getZ(),
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
        Vector3f rot = transform.getRotation();
        if (pos == null) return null;

        float yaw = (rot != null) ? rot.getY() : 0f;
        float pitch = (rot != null) ? rot.getX() : 0f;

        return new SpawnModel(
                world.getName(),
                pos.getX(), pos.getY(), pos.getZ(),
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
    private Vector3f resolveWorldSpawnRotation(@Nonnull World world) {
        String[] candidates = {
                "getSpawnRotation",
                "getDefaultSpawnRotation",
                "getSpawnOrientation",
                "getDefaultSpawnOrientation"
        };

        for (String name : candidates) {
            Object out = tryInvokeNoArgs(world, name);
            if (out instanceof Vector3f v) return v;
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
            Vector3d pos = new Vector3d(spawn.getX(), spawn.getY(), spawn.getZ());
            Vector3f rot = new Vector3f(spawn.getPitch(), spawn.getYaw(), 0f);
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

