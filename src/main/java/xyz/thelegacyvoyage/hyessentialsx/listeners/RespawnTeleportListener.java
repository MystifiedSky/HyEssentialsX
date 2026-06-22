package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;

public final class RespawnTeleportListener {

    private final SpawnManager spawnManager;

    public RespawnTeleportListener(@Nonnull SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new RespawnTeleportTickSystem(spawnManager));
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        if (player == null) return;
        spawnManager.clearRespawnTeleport(player.getUuid());
    }

    private static final class RespawnTeleportTickSystem extends EntityTickingSystem<EntityStore> {

        private final SpawnManager spawnManager;

        private RespawnTeleportTickSystem(@Nonnull SpawnManager spawnManager) {
            this.spawnManager = spawnManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void tick(float deltaTime,
                         int index,
                         ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull CommandBuffer<EntityStore> buffer) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) return;
            SpawnModel pending = spawnManager.peekRespawnTeleport(playerRef.getUuid());
            if (pending == null) return;

            DeathComponent death = chunk.getComponent(index, DeathComponent.getComponentType());
            if (death != null) {
                spawnManager.resetRespawnTeleportDelay(playerRef.getUuid());
                return;
            }

            if (!spawnManager.tickRespawnTeleport(playerRef.getUuid(), deltaTime)) {
                return;
            }

            String err = TeleportationUtil.teleportToSpawn(buffer, chunk.getReferenceTo(index), pending);
            if (err != null) {
                Log.warn("Deferred respawn teleport failed: " + err);
            }
            spawnManager.clearRespawnTeleport(playerRef.getUuid());
        }
    }
}
