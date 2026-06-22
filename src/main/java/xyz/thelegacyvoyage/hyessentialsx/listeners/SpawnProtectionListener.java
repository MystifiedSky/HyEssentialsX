package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public final class SpawnProtectionListener {

    private static final String BYPASS_PERMISSION = "hyessentialsx.spawn.bypass";

    private final SpawnManager spawnManager;
    private final ConfigManager config;
    private static final Set<java.util.UUID> INVULN_TRACK = new HashSet<>();

    public SpawnProtectionListener(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
        this.spawnManager = spawnManager;
        this.config = config;
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new BreakBlockProtectionSystem(spawnManager, config));
        registry.registerSystem(new PlaceBlockProtectionSystem(spawnManager, config));
        registry.registerSystem(new DamageBlockProtectionSystem(spawnManager, config));
        registry.registerSystem(new SpawnDamageProtectionSystem(spawnManager, config));
        registry.registerSystem(new SpawnInvulnerableSystem(spawnManager, config));
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerInteractEvent.class, event -> {
            if (!config.isSpawnProtectionEnabled() || config.isSpawnProtectionAllowInteract()) {
                return;
            }

            if (event.isCancelled()) {
                return;
            }

            PlayerRef player = event.getPlayer().getPlayerRef();
            if (player == null || canBypass(player)) {
                return;
            }

            Vector3i target = event.getTargetBlock();
            if (target == null) {
                return;
            }

            Ref<EntityStore> ref = event.getPlayerRef();
            Store<EntityStore> store = ref.getStore();
            Player playerEntity = event.getPlayer();
            if (playerEntity == null) return;
            if (isProtected(target, player, playerEntity, store, spawnManager, config)) {
                event.setCancelled(true);
            }
        });
    }

    private static boolean canBypass(@Nonnull PlayerRef player) {
        return PermissionsModule.get().hasPermission(player.getUuid(), BYPASS_PERMISSION);
    }

    private static boolean isProtected(@Nonnull Vector3i pos,
                                       @Nonnull PlayerRef player,
                                       @Nonnull Player playerEntity,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull SpawnManager spawnManager,
                                       @Nonnull ConfigManager config) {
        if (!config.isSpawnProtectionEnabled()) return false;
        int radius = Math.max(0, config.getSpawnProtectionRadius());
        if (radius == 0) return false;

        World world = playerEntity.getWorld();
        if (world == null && player.getWorldUuid() != null) {
            world = Universe.get().getWorld(player.getWorldUuid());
        }
        if (world == null) return false;

        SpawnModel spawn = spawnManager.getSpawn();
        if (spawn == null && config.isUseWorldDefaultSpawnIfUnset()) {
            spawn = spawnManager.getSpawnOrWorldDefault(world, player.getUuid());
        }
        if (spawn == null) return false;
        if (!world.getName().equalsIgnoreCase(spawn.getWorldName())) return false;

        double dx = Math.abs(pos.getX() - spawn.getX());
        double dz = Math.abs(pos.getZ() - spawn.getZ());
        return dx <= radius && dz <= radius;
    }

    private static boolean isProtected(@Nonnull com.hypixel.hytale.math.vector.Vector3d pos,
                                       @Nonnull PlayerRef player,
                                       @Nonnull Player playerEntity,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull SpawnManager spawnManager,
                                       @Nonnull ConfigManager config) {
        if (!config.isSpawnProtectionEnabled()) return false;
        int radius = Math.max(0, config.getSpawnProtectionRadius());
        if (radius == 0) return false;

        World world = playerEntity.getWorld();
        if (world == null && player.getWorldUuid() != null) {
            world = Universe.get().getWorld(player.getWorldUuid());
        }
        if (world == null) return false;

        SpawnModel spawn = spawnManager.getSpawn();
        if (spawn == null && config.isUseWorldDefaultSpawnIfUnset()) {
            spawn = spawnManager.getSpawnOrWorldDefault(world, player.getUuid());
        }
        if (spawn == null) return false;
        if (!world.getName().equalsIgnoreCase(spawn.getWorldName())) return false;

        double dx = Math.abs(pos.getX() - spawn.getX());
        double dz = Math.abs(pos.getZ() - spawn.getZ());
        return dx <= radius && dz <= radius;
    }

    private static final class BreakBlockProtectionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final SpawnManager spawnManager;
        private final ConfigManager config;

        private BreakBlockProtectionSystem(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
            super(BreakBlockEvent.class);
            this.spawnManager = spawnManager;
            this.config = config;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
            return java.util.Collections.singleton(RootDependency.first());
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull BreakBlockEvent event) {
            if (!config.isSpawnProtectionEnabled() || config.isSpawnProtectionAllowBreak()) return;
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || canBypass(player)) return;
            Vector3i pos = event.getTargetBlock();
            if (pos == null) return;
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity == null) return;
            if (isProtected(pos, player, playerEntity, store, spawnManager, config)) {
                event.setCancelled(true);
            }
        }
    }

    private static final class PlaceBlockProtectionSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final SpawnManager spawnManager;
        private final ConfigManager config;

        private PlaceBlockProtectionSystem(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
            super(PlaceBlockEvent.class);
            this.spawnManager = spawnManager;
            this.config = config;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
            return java.util.Collections.singleton(RootDependency.first());
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull PlaceBlockEvent event) {
            if (!config.isSpawnProtectionEnabled() || config.isSpawnProtectionAllowPlace()) return;
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || canBypass(player)) return;
            Vector3i pos = event.getTargetBlock();
            if (pos == null) return;
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity == null) return;
            if (isProtected(pos, player, playerEntity, store, spawnManager, config)) {
                event.setCancelled(true);
            }
        }
    }

    private static final class DamageBlockProtectionSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {
        private final SpawnManager spawnManager;
        private final ConfigManager config;

        private DamageBlockProtectionSystem(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
            super(DamageBlockEvent.class);
            this.spawnManager = spawnManager;
            this.config = config;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
            return java.util.Collections.singleton(RootDependency.first());
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull DamageBlockEvent event) {
            if (!config.isSpawnProtectionEnabled() || config.isSpawnProtectionAllowDamage()) return;
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || canBypass(player)) return;
            Vector3i pos = event.getTargetBlock();
            if (pos == null) return;
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity == null) return;
            if (isProtected(pos, player, playerEntity, store, spawnManager, config)) {
                event.setCancelled(true);
            }
        }
    }

    private static final class SpawnDamageProtectionSystem extends DamageEventSystem {
        private final SpawnManager spawnManager;
        private final ConfigManager config;

        private SpawnDamageProtectionSystem(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
            this.spawnManager = spawnManager;
            this.config = config;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
            return java.util.Collections.singleton(RootDependency.first());
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
            if (!config.isSpawnProtectionEnabled() || config.isSpawnProtectionAllowDamage()) return;
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || canBypass(player)) return;
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity == null) return;
            com.hypixel.hytale.math.vector.Vector3d pos = null;
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null && transform.getPosition() != null) {
                pos = transform.getPosition();
            } else if (player.getTransform() != null) {
                pos = player.getTransform().getPosition();
            }
            if (pos == null) return;
            if (isProtected(pos, player, playerEntity, store, spawnManager, config)) {
                event.setCancelled(true);
                event.setAmount(0f);
            }
        }
    }

    private static final class SpawnInvulnerableSystem extends RefChangeSystem<EntityStore, TransformComponent> {
        private final SpawnManager spawnManager;
        private final ConfigManager config;

        private SpawnInvulnerableSystem(@Nonnull SpawnManager spawnManager, @Nonnull ConfigManager config) {
            this.spawnManager = spawnManager;
            this.config = config;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void onComponentAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull TransformComponent component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) {
            updateInvulnerable(ref, component, store, buffer);
        }

        @Override
        public void onComponentSet(
                @Nonnull Ref<EntityStore> ref,
                TransformComponent oldC,
                @Nonnull TransformComponent newC,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) {
            updateInvulnerable(ref, newC, store, buffer);
        }

        @Override
        public void onComponentRemoved(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull TransformComponent component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> buffer
        ) {
            if (!INVULN_TRACK.remove(getPlayerUuid(ref, store))) {
                return;
            }
            buffer.tryRemoveComponent(ref, Invulnerable.getComponentType());
        }

        @Override
        public com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> componentType() {
            return TransformComponent.getComponentType();
        }

        private void updateInvulnerable(@Nonnull Ref<EntityStore> ref,
                                        @Nonnull TransformComponent transform,
                                        @Nonnull Store<EntityStore> store,
                                        @Nonnull CommandBuffer<EntityStore> buffer) {
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || canBypass(player)) {
                clearInvulnerable(ref, buffer, player);
                return;
            }
            if (!config.isSpawnProtectionEnabled() || config.isSpawnProtectionAllowDamage()) {
                clearInvulnerable(ref, buffer, player);
                return;
            }
            if (transform.getPosition() == null) {
                clearInvulnerable(ref, buffer, player);
                return;
            }
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity == null) {
                clearInvulnerable(ref, buffer, player);
                return;
            }
            boolean inProtected = isProtected(transform.getPosition(), player, playerEntity, store, spawnManager, config);
            if (inProtected) {
                INVULN_TRACK.add(player.getUuid());
                buffer.addComponent(ref, Invulnerable.getComponentType());
            } else {
                clearInvulnerable(ref, buffer, player);
            }
        }

        private void clearInvulnerable(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull CommandBuffer<EntityStore> buffer,
                                       PlayerRef player) {
            if (player != null && !INVULN_TRACK.remove(player.getUuid())) {
                return;
            }
            buffer.tryRemoveComponent(ref, Invulnerable.getComponentType());
        }

        private java.util.UUID getPlayerUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            return player != null ? player.getUuid() : new java.util.UUID(0L, 0L);
        }
    }
}
