package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.protocol.packets.connection.DisconnectType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.EntitySource;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.Source;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.CombatLogManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

public final class CombatLogListener {

    private final CombatLogManager combatManager;
    private final ConfigManager config;

    public CombatLogListener(@Nonnull CombatLogManager combatManager, @Nonnull ConfigManager config) {
        this.combatManager = combatManager;
        this.config = config;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(ShutdownEvent.class, event -> combatManager.setShutdownInProgress(true));
        events.registerGlobal(PlayerDisconnectEvent.class, event -> {
            if (!config.isCombatLogEnabled()) return;
            PlayerRef player = event.getPlayerRef();
            if (player != null) {
                PacketHandler.DisconnectReason reason = event.getDisconnectReason();
                if (combatManager.isServerShuttingDown() || isServerRestartDisconnect(reason)) {
                    combatManager.remove(player.getUuid());
                    return;
                }
                combatManager.handleDisconnect(player);
            }
        });
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new CombatLogDamageSystem(combatManager, config));
        registry.registerSystem(new CombatLogExpirySystem(combatManager, config));
        registry.registerSystem(new CombatLogDeathSystem(combatManager, config));
    }

    private static final class CombatLogDamageSystem extends DamageEventSystem {

        private final CombatLogManager combatManager;
        private final ConfigManager config;

        private CombatLogDamageSystem(@Nonnull CombatLogManager combatManager, @Nonnull ConfigManager config) {
            this.combatManager = combatManager;
            this.config = config;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getInspectDamageGroup();
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull Damage damage) {
            if (!config.isCombatLogEnabled()) return;
            if (damage.isCancelled() || damage.getAmount() <= 0f) return;

            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            PlayerRef victim = store.getComponent(victimRef, PlayerRef.getComponentType());
            if (victim == null || store.getComponent(victimRef, Player.getComponentType()) == null) return;

            boolean onlyPlayer = config.isCombatLogOnlyPlayerDamage();
            PlayerRef attacker = resolveAttacker(store, damage);
            boolean attackerIsPlayer = attacker != null;

            if (attackerIsPlayer) {
                tagIfAllowed(victim, store);
                tagIfAllowed(attacker, store);
                return;
            }

            if (!onlyPlayer && isEntitySource(damage)) {
                tagIfAllowed(victim, store);
            }
        }

        private boolean isEntitySource(@Nonnull Damage damage) {
            return damage.getSource() instanceof EntitySource;
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

        private void tagIfAllowed(@Nonnull PlayerRef player, @Nonnull Store<EntityStore> store) {
            if (PermissionsModule.get().hasPermission(player.getUuid(), CombatLogManager.BYPASS_PERMISSION)) {
                return;
            }
            String worldName = store.getExternalData().getWorld() != null
                    ? store.getExternalData().getWorld().getName()
                    : null;
            combatManager.tag(player, worldName);
        }
    }

    private static final class CombatLogExpirySystem extends TickingSystem<EntityStore> {

        private final CombatLogManager combatManager;
        private final ConfigManager config;

        private CombatLogExpirySystem(@Nonnull CombatLogManager combatManager, @Nonnull ConfigManager config) {
            this.combatManager = combatManager;
            this.config = config;
        }

        @Override
        public void tick(float dt, int index, @Nonnull Store<EntityStore> store) {
            if (!config.isCombatLogEnabled()) return;
            combatManager.tickExpiry();
        }
    }

    private static final class CombatLogDeathSystem extends RefChangeSystem<EntityStore, DeathComponent> {

        private final CombatLogManager combatManager;
        private final ConfigManager config;

        private CombatLogDeathSystem(@Nonnull CombatLogManager combatManager, @Nonnull ConfigManager config) {
            this.combatManager = combatManager;
            this.config = config;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull DeathComponent component,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull CommandBuffer<EntityStore> buffer) {
            if (!config.isCombatLogEnabled()) return;
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null) {
                combatManager.removeIfDead(player.getUuid());
            }
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref,
                                   DeathComponent oldC,
                                   @Nonnull DeathComponent newC,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull CommandBuffer<EntityStore> buffer) { }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull DeathComponent component,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> buffer) { }

        @Override
        public com.hypixel.hytale.component.ComponentType<EntityStore, DeathComponent> componentType() {
            return DeathComponent.getComponentType();
        }
    }

    private boolean isServerRestartDisconnect(@Nullable PacketHandler.DisconnectReason reason) {
        if (reason == null) return false;
        DisconnectType type = reason.getClientDisconnectType();
        if (type == DisconnectType.Crash) {
            return true;
        }
        String serverReason = reason.getServerDisconnectReason();
        if (serverReason == null || serverReason.isBlank()) return false;
        String lower = serverReason.toLowerCase(Locale.ROOT);
        return lower.contains("shutdown") || lower.contains("restart") || lower.contains("stopping");
    }
}

