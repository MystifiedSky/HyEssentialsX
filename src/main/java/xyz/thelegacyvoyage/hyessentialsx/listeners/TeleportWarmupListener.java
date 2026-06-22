package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class TeleportWarmupListener {

    private final TPManager tpManager;

    public TeleportWarmupListener(@Nonnull TPManager tpManager) {
        this.tpManager = tpManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new TeleportWarmupTickSystem(tpManager));
    }

    private static final class TeleportWarmupTickSystem extends EntityTickingSystem<EntityStore> {

        private final TPManager tpManager;

        private TeleportWarmupTickSystem(@Nonnull TPManager tpManager) {
            this.tpManager = tpManager;
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
            if (!tpManager.hasPending(playerRef.getUuid())) return;

            Transform transform = playerRef.getTransform();
            if (transform == null) return;
            Vector3d pos = transform.getPosition();
            if (pos == null) return;

            TPManager.TickResult result = tpManager.tickStatus(playerRef.getUuid(), pos, deltaTime);
            if (result.status() == TPManager.TickResult.Status.CANCELLED) {
                Messages.sendPrefixedKey(playerRef, "teleport.cancelled_moved", Map.of());
                return;
            }
            if (result.status() == TPManager.TickResult.Status.READY) {
                TPManager.TeleportAction action = result.action();
                if (action != null) {
                    action.execute(buffer);
                }
            }
        }
    }
}
