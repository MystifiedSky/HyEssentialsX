package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;

public final class DeathSpawnListener {

    private final SpawnManager spawnManager;
    private final ConfigManager configManager;

    public DeathSpawnListener(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager configManager) {
        this.spawnManager = spawnManager;
        this.configManager = configManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new DeathSpawnSys(spawnManager, configManager));
    }

    private static final class DeathSpawnSys extends RefChangeSystem<EntityStore, DeathComponent> {

        private final SpawnManager spawnManager;
        private final ConfigManager configManager;

        private DeathSpawnSys(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager configManager) {
            this.spawnManager = spawnManager;
            this.configManager = configManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void onComponentAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull DeathComponent component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) {
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;

            World world = store.getExternalData().getWorld();
            if (world == null) return;

            if (!configManager.isSpawnEnabled()) return;

            String worldName = world.getName();
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity != null && hasRespawnPoint(playerEntity, worldName)) {
                Transform respawn = null;
                try {
                    respawn = Player.getRespawnPosition(ref, worldName, store);
                } catch (Throwable ignored) {
                }
                if (respawn != null && respawn.getPosition() != null) {
                    Vector3d pos = respawn.getPosition();
                    Vector3f rot = respawn.getRotation();
                    float yaw = (rot != null) ? rot.getY() : 0f;
                    float pitch = (rot != null) ? rot.getX() : 0f;
                    String err = TeleportationUtil.teleportToLocation(
                            buffer,
                            ref,
                            worldName,
                            pos.getX(), pos.getY(), pos.getZ(),
                            yaw, pitch
                    );
                    if (err != null) {
                        Log.warn("Respawn teleport failed: " + err);
                    }
                    return;
                }
            }

            SpawnModel spawn = spawnManager.getSpawn();
            if (spawn == null && configManager.isUseWorldDefaultSpawnIfUnset()) {
                spawn = spawnManager.getSpawnOrWorldDefault(world, player.getUuid());
            }
            if (spawn == null) return;

            String err = TeleportationUtil.teleportToSpawn(store, ref, spawn, buffer);
            if (err != null) {
                Log.warn("Respawn teleport failed: " + err);
            }
        }

        private boolean hasRespawnPoint(@Nonnull Player player, @Nonnull String worldName) {
            try {
                var config = player.getPlayerConfigData();
                if (config == null) return false;
                var perWorld = config.getPerWorldData(worldName);
                if (perWorld == null) return false;
                var points = perWorld.getRespawnPoints();
                return points != null && points.length > 0;
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override
        public void onComponentSet(
                @Nonnull Ref<EntityStore> ref,
                DeathComponent oldC,
                @Nonnull DeathComponent newC,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) { }

        @Override
        public void onComponentRemoved(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull DeathComponent component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) { }

        @Override
        public com.hypixel.hytale.component.ComponentType<EntityStore, DeathComponent> componentType() {
            return DeathComponent.getComponentType();
        }
    }
}
