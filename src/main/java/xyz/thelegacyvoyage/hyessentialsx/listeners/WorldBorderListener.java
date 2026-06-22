package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import xyz.thelegacyvoyage.hyessentialsx.managers.WorldBorderManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.Map;

public final class WorldBorderListener {

    private final WorldBorderManager worldBorderManager;

    public WorldBorderListener(@Nonnull WorldBorderManager worldBorderManager) {
        this.worldBorderManager = worldBorderManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new WorldBorderTickSystem(worldBorderManager));
    }

    private static final class WorldBorderTickSystem extends EntityTickingSystem<EntityStore> {

        private final WorldBorderManager worldBorderManager;

        private WorldBorderTickSystem(@Nonnull WorldBorderManager worldBorderManager) {
            this.worldBorderManager = worldBorderManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void tick(float deltaTime,
                         int index,
                         @Nonnull ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull CommandBuffer<EntityStore> buffer) {
            worldBorderManager.tickExpansion();
            if (!worldBorderManager.isEnabled()) {
                return;
            }

            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) return;
            Transform transform = playerRef.getTransform();
            if (transform == null) return;
            Vector3d position = transform.getPosition();
            if (position == null || !worldBorderManager.isOutside(position)) return;
            if (store.getExternalData() == null) return;
            World world = store.getExternalData().getWorld();
            if (world == null) return;

            Vector3d destination = worldBorderManager.clampInside(position);
            Rotation3f rotation = transform.getRotation();
            float yaw = rotation != null ? rotation.y() : 0f;
            float pitch = rotation != null ? rotation.x() : 0f;
            TeleportationUtil.teleportToLocation(
                    buffer,
                    playerRef.getReference(),
                    world.getName(),
                    destination.x(),
                    destination.y(),
                    destination.z(),
                    yaw,
                    pitch
            );
            if (worldBorderManager.shouldWarn(playerRef.getUuid())) {
                Messages.sendPrefixedKey(playerRef, "worldborder.warning", Map.of());
            }
        }
    }
}
