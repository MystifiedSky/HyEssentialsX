package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
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
