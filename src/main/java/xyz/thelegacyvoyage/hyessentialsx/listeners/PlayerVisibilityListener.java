package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;
import xyz.thelegacyvoyage.hyessentialsx.util.MapVisibilityUtil;

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
            // Clean up legacy vanish component to avoid client crashes in third person.
            try {
                store.removeComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers.getComponentType());
            } catch (IllegalArgumentException ignored) {
                // Component not in archetype; safe to ignore.
            }

            // If the player isn't vanished, make sure any stale hidden state is cleared.
            if (!vanishManager.isEnabled(component.getUuid())) {
                for (PlayerRef viewer : Universe.get().getPlayers()) {
                    if (viewer == null) continue;
                    HiddenPlayersManager manager = viewer.getHiddenPlayersManager();
                    if (manager != null) {
                        manager.showPlayer(component.getUuid());
                    }
                }
            }

            // Hide currently vanished players from the joining player.
            HiddenPlayersManager hidden = component.getHiddenPlayersManager();
            if (hidden != null) {
                for (var vanishedId : vanishManager.getVanishedPlayers()) {
                    if (!vanishedId.equals(component.getUuid())) {
                        hidden.hidePlayer(vanishedId);
                    }
                }
            }

            // Ensure map/compass markers respect vanish state for this player.
            MapVisibilityUtil.applyForViewer(component, vanishManager);

            // If this player is vanished (should be rare), hide them from others.
            if (vanishManager.isEnabled(component.getUuid())) {
                for (PlayerRef viewer : Universe.get().getPlayers()) {
                    if (viewer == null || viewer.getUuid().equals(component.getUuid())) continue;
                    HiddenPlayersManager manager = viewer.getHiddenPlayersManager();
                    if (manager != null) {
                        manager.hidePlayer(component.getUuid());
                    }
                }
            }
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

