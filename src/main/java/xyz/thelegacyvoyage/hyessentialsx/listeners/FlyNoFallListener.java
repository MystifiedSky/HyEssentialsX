package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

public final class FlyNoFallListener {

    private final FlyManager flyManager;

    public FlyNoFallListener(@Nonnull FlyManager flyManager) {
        this.flyManager = flyManager;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new FlyNoFallTickSystem(flyManager));
        registry.registerSystem(new FlyNoFallDamageSystem(flyManager));
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
            if (flyManager.isApplyPending(playerRef.getUuid())) {
                flyManager.tryApplyIfPending(playerRef);
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

