package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep.MorningWakeUp;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep.NoddingOff;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep.Slumber;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FlyNoFallListener {

    private final FlyManager flyManager;

    public FlyNoFallListener(@Nonnull FlyManager flyManager) {
        this.flyManager = flyManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new FlyNoFallPreDamageSystem(flyManager));
        registry.registerSystem(new FlyNoFallTickSystem(flyManager));
        registry.registerSystem(new FlyNoFallDamageSystem(flyManager));
    }

    private static final class FlyNoFallPreDamageSystem extends EntityTickingSystem<EntityStore> {

        private final FlyManager flyManager;

        private FlyNoFallPreDamageSystem(@Nonnull FlyManager flyManager) {
            this.flyManager = flyManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getGatherDamageGroup();
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(
                    new SystemDependency<>(Order.BEFORE, DamageSystems.FallDamagePlayers.class),
                    new SystemDependency<>(Order.BEFORE, DamageSystems.FallDamageNPCs.class)
            );
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

    private static final class FlyNoFallTickSystem extends EntityTickingSystem<EntityStore> {

        private final FlyManager flyManager;
        private final Map<UUID, Boolean> sleepingState = new ConcurrentHashMap<>();
        private final Map<UUID, Boolean> mountedState = new ConcurrentHashMap<>();

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
            UUID playerId = playerRef.getUuid();
            if (flyManager.isApplyPending(playerId)) {
                flyManager.tryApplyIfPending(playerRef);
            }
            if (!flyManager.isEnabled(playerId)) {
                sleepingState.remove(playerId);
                mountedState.remove(playerId);
                return;
            }

            boolean isSleeping = isSleeping(chunk, index);
            boolean wasSleeping = sleepingState.getOrDefault(playerId, Boolean.FALSE);
            sleepingState.put(playerId, isSleeping);
            if (wasSleeping && !isSleeping) {
                // Beds can silently reset movement controls; force a one-time re-apply on wake up.
                flyManager.applyState(playerRef, true);
            }

            boolean isMounted = isMounted(chunk, index);
            boolean wasMounted = mountedState.getOrDefault(playerId, Boolean.FALSE);
            mountedState.put(playerId, isMounted);
            if (wasMounted && !isMounted) {
                // Chairs and other mounts can clear movement settings on dismount.
                if (!flyManager.applyState(playerRef, true)) {
                    flyManager.queueApply(playerId, true);
                }
            }

            // Portals/world changes can reset movement settings; re-apply fly ability.
            MovementManager movementManager = chunk.getComponent(index, MovementManager.getComponentType());
            if (movementManager != null) {
                boolean changed = false;
                MovementSettings settings = movementManager.getSettings();
                if (settings != null && !settings.canFly) {
                    settings.canFly = true;
                    changed = true;
                }
                MovementSettings defaults = movementManager.getDefaultSettings();
                if (defaults != null && !defaults.canFly) {
                    defaults.canFly = true;
                    changed = true;
                }
                if (changed) {
                    movementManager.update(playerRef.getPacketHandler());
                }
            }

            Player player = chunk.getComponent(index, Player.getComponentType());
            if (player == null) return;

            if (player.getCurrentFallDistance() > 0d) {
                player.setCurrentFallDistance(0d);
            }
        }

        private boolean isSleeping(@Nonnull ArchetypeChunk<EntityStore> chunk, int index) {
            PlayerSomnolence somnolence = chunk.getComponent(index, PlayerSomnolence.getComponentType());
            if (somnolence == null) return false;
            PlayerSleep state = somnolence.getSleepState();
            return state instanceof Slumber || state instanceof NoddingOff || state instanceof MorningWakeUp;
        }

        private boolean isMounted(@Nonnull ArchetypeChunk<EntityStore> chunk, int index) {
            return chunk.getComponent(index, MountedComponent.getComponentType()) != null;
        }
    }

    private static final class FlyNoFallDamageSystem extends DamageEventSystem {

        private final FlyManager flyManager;

        private FlyNoFallDamageSystem(@Nonnull FlyManager flyManager) {
            this.flyManager = flyManager;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(
                    new SystemGroupDependency<>(Order.AFTER, DamageModule.get().getFilterDamageGroup()),
                    new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class)
            );
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull Damage event) {
            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) return;
            if (!isFlyEnabled(chunk, index, playerRef)) return;

            if (!isFallDamage(event)) return;

            event.setCancelled(true);
            event.setAmount(0f);
        }

        private boolean isFlyEnabled(@Nonnull ArchetypeChunk<EntityStore> chunk,
                                     int index,
                                     @Nonnull PlayerRef playerRef) {
            if (flyManager.isEnabled(playerRef.getUuid())) return true;
            MovementManager movementManager = chunk.getComponent(index, MovementManager.getComponentType());
            if (movementManager == null) return false;
            MovementSettings settings = movementManager.getSettings();
            if (settings != null && settings.canFly) return true;
            MovementSettings defaults = movementManager.getDefaultSettings();
            return defaults != null && defaults.canFly;
        }

        private static boolean isFallDamage(@Nonnull Damage event) {
            String cause = resolveCauseName(event);
            if (cause != null && cause.contains("fall")) return true;

            Damage.Source source = event.getSource();
            if (source == null) return false;
            String sourceName = source.getClass().getSimpleName();
            if (sourceName != null && sourceName.toLowerCase().contains("fall")) return true;
            String sourceText = source.toString();
            return sourceText != null && sourceText.toLowerCase().contains("fall");
        }

        private static String resolveCauseName(@Nonnull Damage event) {
            Object value = tryInvoke(event, "getDamageCause");
            if (value == null) value = tryInvoke(event, "getCause");
            if (value == null) value = tryInvoke(event, "getDamageType");
            if (value == null) value = tryInvoke(event, "getType");
            if (value == null) value = tryInvoke(event, "getSource");
            if (value == null) value = tryInvoke(event, "getReason");
            if (value == null) return null;

            String name = value.toString();
            if (name == null) return null;
            return name.toLowerCase();
        }

        private static Object tryInvoke(@Nonnull Object target, @Nonnull String method) {
            try {
                Method m = target.getClass().getMethod(method);
                return m.invoke(target);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}

