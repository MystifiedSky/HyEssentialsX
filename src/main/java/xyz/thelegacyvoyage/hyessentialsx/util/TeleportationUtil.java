package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

public final class TeleportationUtil {

    private TeleportationUtil() {}

    /** Hytale pitch bug avoidance - keep pitch 0 and round yaw to cardinal. */
    public static float roundYawCardinal(float yawRadians) {
        float deg = (float) Math.toDegrees(yawRadians);
        deg = deg % 360f;
        if (deg > 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;

        if (deg >= -45 && deg < 45) return 0f; // north
        if (deg >= 45 && deg < 135) return (float) Math.toRadians(90); // west
        if (deg >= 135 || deg < -135) return (float) Math.PI; // south
        return (float) Math.toRadians(-90); // east
    }

    @Nullable
    public static String teleportToSpawn(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull SpawnModel spawn
    ) {
        return teleportToSpawn(store, ref, spawn, null);
    }

    @Nullable
    public static String teleportToSpawn(
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull SpawnModel spawn
    ) {
        World targetWorld = Universe.get().getWorld(spawn.getWorldName());
        if (targetWorld == null) {
            return "World '" + spawn.getWorldName() + "' is not loaded.";
        }

        Vector3d pos = new Vector3d(spawn.getX(), spawn.getY(), spawn.getZ());
        Vector3f rot = new Vector3f(0, roundYawCardinal(spawn.getYaw()), 0);

        Teleport tp = Teleport.createForPlayer(targetWorld, pos, rot);
        if (!tryPutTeleport(buffer, ref, tp)) {
            return "Teleport failed to queue.";
        }
        return null;
    }

    @Nullable
    public static String teleportToSpawn(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull SpawnModel spawn,
            @Nullable CommandBuffer<EntityStore> buffer
    ) {
        World targetWorld = Universe.get().getWorld(spawn.getWorldName());
        if (targetWorld == null) {
            return "World '" + spawn.getWorldName() + "' is not loaded.";
        }

        Vector3d pos = new Vector3d(spawn.getX(), spawn.getY(), spawn.getZ());
        Vector3f rot = new Vector3f(0, roundYawCardinal(spawn.getYaw()), 0);

        Teleport tp = Teleport.createForPlayer(targetWorld, pos, rot);
        if (!tryPutTeleport(buffer, ref, tp)) {
            store.putComponent(ref, Teleport.getComponentType(), tp);
        }
        return null;
    }

    @Nullable
    public static String teleportToPlayer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef target
    ) {
        Transform targetTransform = target.getTransform();
        if (targetTransform == null) {
            return "Could not read target position.";
        }

        World targetWorld = Universe.get().getWorld(target.getWorldUuid());
        if (targetWorld == null) {
            return "Target world is not loaded.";
        }

        Teleport tp = Teleport.createForPlayer(targetWorld, targetTransform);
        store.putComponent(ref, Teleport.getComponentType(), tp);
        return null;
    }

    @Nullable
    public static String teleportToLocation(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull String worldName,
            double x, double y, double z,
            float yaw, float pitch
    ) {
        return teleportToLocation(store, ref, null, worldName, x, y, z, yaw, pitch);
    }

    @Nullable
    public static String teleportToLocation(
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull String worldName,
            double x, double y, double z,
            float yaw, float pitch
    ) {
        return teleportToLocation(buffer, ref, null, worldName, x, y, z, yaw, pitch);
    }

    @Nullable
    public static String teleportToLocation(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nullable String worldId,
            @Nullable String worldName,
            double x, double y, double z,
            float yaw, float pitch
    ) {
        World targetWorld = resolveWorld(worldId, worldName);
        if (targetWorld == null) {
            return "World '" + safeWorldLabel(worldId, worldName) + "' is not loaded.";
        }

        Vector3d pos = new Vector3d(x, y, z);
        Vector3f rot = new Vector3f(pitch, yaw, 0f);
        Teleport tp = Teleport.createForPlayer(targetWorld, pos, rot);
        store.putComponent(ref, Teleport.getComponentType(), tp);
        return null;
    }

    @Nullable
    public static String teleportToLocation(
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull Ref<EntityStore> ref,
            @Nullable String worldId,
            @Nullable String worldName,
            double x, double y, double z,
            float yaw, float pitch
    ) {
        World targetWorld = resolveWorld(worldId, worldName);
        if (targetWorld == null) {
            return "World '" + safeWorldLabel(worldId, worldName) + "' is not loaded.";
        }

        Vector3d pos = new Vector3d(x, y, z);
        Vector3f rot = new Vector3f(pitch, yaw, 0f);
        Teleport tp = Teleport.createForPlayer(targetWorld, pos, rot);
        if (!tryPutTeleport(buffer, ref, tp)) {
            return "Teleport failed to queue.";
        }
        return null;
    }

    @Nullable
    private static World resolveWorld(@Nullable String worldId, @Nullable String worldName) {
        if (worldId != null && !worldId.isBlank()) {
            try {
                World byId = Universe.get().getWorld(UUID.fromString(worldId));
                if (byId != null) return byId;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (worldName != null && !worldName.isBlank()) {
            World byName = Universe.get().getWorld(worldName);
            if (byName != null) return byName;
        }
        return Universe.get().getWorld("default");
    }

    private static String safeWorldLabel(@Nullable String worldId, @Nullable String worldName) {
        if (worldName != null && !worldName.isBlank()) return worldName;
        if (worldId != null && !worldId.isBlank()) return worldId;
        return "default";
    }

    private static boolean tryPutTeleport(
            @Nullable CommandBuffer<EntityStore> buffer,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Teleport tp
    ) {
        if (buffer == null) return false;

        Object componentType = Teleport.getComponentType();
        String[] methodNames = {"putComponent", "setComponent", "addComponent"};

        for (String name : methodNames) {
            for (Method method : buffer.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() != 3) continue;
                try {
                    method.invoke(buffer, ref, componentType, tp);
                    return true;
                } catch (Throwable ignored) {
                    // Try other overloads/names.
                }
            }
        }
        return false;
    }
}
