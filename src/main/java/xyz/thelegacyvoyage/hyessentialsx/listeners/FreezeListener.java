package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.EntitySource;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.Source;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreezeManager;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FreezeListener {

    private final FreezeManager freezeManager;

    public FreezeListener(@Nonnull FreezeManager freezeManager) {
        this.freezeManager = freezeManager;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerConnectEvent.class, event -> {
            PlayerRef player = event.getPlayerRef();
            if (player != null) {
                freezeManager.handleJoin(player);
            }
        });

        events.registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            Store<EntityStore> store = ref.getStore();
            if (store == null) return;
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;
            if (freezeManager.isFrozen(player.getUuid())) {
                freezeManager.updateState(player);
            }
        });

        events.registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef player = event.getPlayerRef();
            if (player != null) {
                freezeManager.handleDisconnect(player.getUuid());
            }
        });

        events.registerGlobal(PlayerInteractEvent.class, event -> {
            PlayerRef player = event.getPlayer().getPlayerRef();
            if (player == null) return;
            if (!freezeManager.isFrozen(player.getUuid())) return;
            event.setCancelled(true);
        });
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new FreezeMovementSystem(freezeManager));
        registry.registerSystem(new FreezeDamageSystem(freezeManager));
        registry.registerSystem(new FreezeBreakBlockSystem(freezeManager));
        registry.registerSystem(new FreezePlaceBlockSystem(freezeManager));
        registry.registerSystem(new FreezeDamageBlockSystem(freezeManager));
    }

    private static final class FreezeMovementSystem extends EntityTickingSystem<EntityStore> {

        private final FreezeManager freezeManager;

        private FreezeMovementSystem(@Nonnull FreezeManager freezeManager) {
            this.freezeManager = freezeManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void tick(float deltaTime,
                         int index,
                         @Nonnull ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull CommandBuffer<EntityStore> buffer) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) return;
            if (!freezeManager.isFrozen(playerRef.getUuid())) return;

            freezeManager.updateState(playerRef);
            FreezeManager.FrozenState state = freezeManager.getState(playerRef.getUuid());
            if (state == null) {
                return;
            }

            if (playerRef.getTransform() == null || playerRef.getTransform().getPosition() == null) return;
            Vector3d pos = playerRef.getTransform().getPosition();
            if (pos == null) return;

            if (movedTooFar(pos, state)) {
                TeleportationUtil.teleportToLocation(
                        buffer,
                        playerRef.getReference(),
                        state.worldName(),
                        state.x(),
                        state.y(),
                        state.z(),
                        state.yaw(),
                        state.pitch()
                );
            }
        }

        private boolean movedTooFar(@Nonnull Vector3d pos, @Nonnull FreezeManager.FrozenState state) {
            double dx = pos.getX() - state.x();
            double dy = pos.getY() - state.y();
            double dz = pos.getZ() - state.z();
            return (dx * dx + dy * dy + dz * dz) > 0.01;
        }
    }

    private static final class FreezeDamageSystem extends DamageEventSystem {

        private final FreezeManager freezeManager;

        private FreezeDamageSystem(@Nonnull FreezeManager freezeManager) {
            this.freezeManager = freezeManager;
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
            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            PlayerRef victim = store.getComponent(victimRef, PlayerRef.getComponentType());
            if (victim != null && freezeManager.isFrozen(victim.getUuid())) {
                event.setCancelled(true);
                event.setAmount(0f);
                return;
            }

            PlayerRef attacker = resolveAttacker(store, event);
            if (attacker != null && freezeManager.isFrozen(attacker.getUuid())) {
                event.setCancelled(true);
                event.setAmount(0f);
            }
        }

        @Nullable
        private PlayerRef resolveAttacker(@Nonnull Store<EntityStore> store, @Nonnull Damage damage) {
            Source source = damage.getSource();
            if (!(source instanceof EntitySource entitySource)) return null;
            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef == null) return null;
            if (store.getComponent(attackerRef, Player.getComponentType()) == null) return null;
            return store.getComponent(attackerRef, PlayerRef.getComponentType());
        }
    }

    private static final class FreezeBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final FreezeManager freezeManager;

        private FreezeBreakBlockSystem(@Nonnull FreezeManager freezeManager) {
            super(BreakBlockEvent.class);
            this.freezeManager = freezeManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull BreakBlockEvent event) {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;
            if (freezeManager.isFrozen(player.getUuid())) {
                event.setCancelled(true);
            }
        }
    }

    private static final class FreezePlaceBlockSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final FreezeManager freezeManager;

        private FreezePlaceBlockSystem(@Nonnull FreezeManager freezeManager) {
            super(PlaceBlockEvent.class);
            this.freezeManager = freezeManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull PlaceBlockEvent event) {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;
            if (freezeManager.isFrozen(player.getUuid())) {
                event.setCancelled(true);
            }
        }
    }

    private static final class FreezeDamageBlockSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {
        private final FreezeManager freezeManager;

        private FreezeDamageBlockSystem(@Nonnull FreezeManager freezeManager) {
            super(DamageBlockEvent.class);
            this.freezeManager = freezeManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull DamageBlockEvent event) {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) return;
            if (freezeManager.isFrozen(player.getUuid())) {
                event.setCancelled(true);
            }
        }
    }
}
