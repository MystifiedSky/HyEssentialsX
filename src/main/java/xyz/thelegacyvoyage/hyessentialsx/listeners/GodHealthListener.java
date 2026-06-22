package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;

import javax.annotation.Nonnull;

public final class GodHealthListener {

    private final GodManager godManager;

    public GodHealthListener(@Nonnull GodManager godManager) {
        this.godManager = godManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new GodDamageSystem(godManager));
    }

    private static final class GodDamageSystem extends DamageEventSystem {

        private final GodManager godManager;

        private GodDamageSystem(@Nonnull GodManager godManager) {
            this.godManager = godManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getFilterDamageGroup();
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull Damage event) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) return;
            if (!godManager.isEnabled(playerRef.getUuid())) return;

            if (event.getAmount() > 0f) {
                event.setCancelled(true);
                event.setAmount(0f);
            }
        }
    }
}

