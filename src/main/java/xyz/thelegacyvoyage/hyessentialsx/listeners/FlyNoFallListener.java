package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;

import javax.annotation.Nonnull;

public final class FlyNoFallListener {

    private final FlyManager flyManager;

    public FlyNoFallListener(@Nonnull FlyManager flyManager) {
        this.flyManager = flyManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new FlyNoFallTickSystem(flyManager));
    }

    private static final class FlyNoFallTickSystem extends EntityTickingSystem<EntityStore> {

        private final FlyManager flyManager;

        private FlyNoFallTickSystem(@Nonnull FlyManager flyManager) {
            this.flyManager = flyManager;
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
            if (!flyManager.isEnabled(playerRef.getUuid())) return;

            Player player = chunk.getComponent(index, Player.getComponentType());
            if (player == null) return;

            if (player.getCurrentFallDistance() > 0d) {
                player.setCurrentFallDistance(0d);
            }
        }
    }
}
