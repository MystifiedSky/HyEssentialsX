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
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;

import javax.annotation.Nonnull;

public final class PlayerVisibilityListener {

    private final VanishManager vanishManager;

    public PlayerVisibilityListener(@Nonnull VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new VisibilityResetSystem(vanishManager));
    }

    private static final class VisibilityResetSystem extends RefChangeSystem<EntityStore, PlayerRef> {

        private final VanishManager vanishManager;

        private VisibilityResetSystem(@Nonnull VanishManager vanishManager) {
            this.vanishManager = vanishManager;
        }

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
            if (vanishManager.isEnabled(component.getUuid())) {
                store.addComponent(ref, HiddenFromAdventurePlayers.getComponentType());
                return;
            }
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
