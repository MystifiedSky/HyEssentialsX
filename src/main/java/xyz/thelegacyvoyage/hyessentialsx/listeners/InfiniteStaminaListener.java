package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.InfiniteStaminaManager;

import javax.annotation.Nonnull;

public final class InfiniteStaminaListener {

    private final InfiniteStaminaManager staminaManager;

    public InfiniteStaminaListener(@Nonnull InfiniteStaminaManager staminaManager) {
        this.staminaManager = staminaManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new InfiniteStaminaTickSystem(staminaManager));
    }

    private static final class InfiniteStaminaTickSystem extends EntityTickingSystem<EntityStore> {

        private final InfiniteStaminaManager staminaManager;

        private InfiniteStaminaTickSystem(@Nonnull InfiniteStaminaManager staminaManager) {
            this.staminaManager = staminaManager;
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
            if (!staminaManager.isEnabled(playerRef.getUuid())) return;

            EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
            if (stats == null) return;

            stats.maximizeStatValue(DefaultEntityStatTypes.getStamina());
            stats.update();
        }
    }
}

