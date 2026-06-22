package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class PlayerVisibilityListener {

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new VisibilityResetSystem());
    }

    private static final class VisibilityResetSystem extends RefChangeSystem<EntityStore, PlayerRef> {

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void onComponentAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) {
            // Ensure players are visible when they spawn in.
            store.removeComponent(ref, HiddenFromAdventurePlayers.getComponentType());
        }

        @Override
        public void onComponentSet(
                @Nonnull Ref<EntityStore> ref,
                PlayerRef oldC,
                @Nonnull PlayerRef newC,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) { }

        @Override
        public void onComponentRemoved(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) { }

        @Override
        public com.hypixel.hytale.component.ComponentType<EntityStore, PlayerRef> componentType() {
            return PlayerRef.getComponentType();
        }
    }
}
