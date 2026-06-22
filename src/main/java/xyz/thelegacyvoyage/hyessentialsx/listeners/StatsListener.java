package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PickupItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import xyz.thelegacyvoyage.hyessentialsx.managers.StatsManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsListener {

    private static final long DAMAGE_TRACK_TIMEOUT_MS = 10_000L;

    private final StatsManager stats;
    private final ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Vector3d> lastPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> accumulatedDistanceCm = new ConcurrentHashMap<>();

    public StatsListener(@Nonnull StatsManager stats) {
        this.stats = stats;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(EventPriority.LATE, PlayerConnectEvent.class, event -> {
            if (!stats.isEnabled() || event.getPlayerRef() == null) return;
            stats.increment(event.getPlayerRef().getUuid(), StatsManager.CATEGORY_CUSTOM, "times_connected");
        });
        events.registerGlobal(EventPriority.LATE, PlayerDisconnectEvent.class, event -> {
            if (event.getPlayerRef() == null) return;
            cleanupPlayer(event.getPlayerRef().getUuid());
        });
        events.registerGlobal(EventPriority.LATE, PlayerChatEvent.class, event -> {
            if (!stats.isEnabled() || event.isCancelled() || event.getSender() == null) return;
            stats.increment(event.getSender().getUuid(), StatsManager.CATEGORY_CUSTOM, "messages_sent");
        });
    }

    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new BlockBreakStatsSystem(stats));
        registry.registerSystem(new BlockPlaceStatsSystem(stats));
        registry.registerSystem(new CraftStatsSystem(stats));
        registry.registerSystem(new DropStatsSystem(stats));
        registry.registerSystem(new PickupStatsSystem(stats));
        registry.registerSystem(new DamageStatsSystem(stats, lastDamage));
        registry.registerSystem(new DeathStatsSystem(stats, lastDamage));
        registry.registerSystem(new MovementStatsSystem(stats, lastPositions, accumulatedDistanceCm));
    }

    private void cleanupPlayer(@Nonnull UUID playerId) {
        lastPositions.remove(playerId);
        accumulatedDistanceCm.remove(playerId);
    }

    private static final class BlockBreakStatsSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final StatsManager stats;

        private BlockBreakStatsSystem(@Nonnull StatsManager stats) {
            super(BreakBlockEvent.class);
            this.stats = stats;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
            return Collections.singleton(RootDependency.first());
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull BreakBlockEvent event) {
            if (!stats.isEnabled() || event.isCancelled()) return;
            BlockType blockType = event.getBlockType();
            if (blockType == null || blockType.getId() == null || "Empty".equalsIgnoreCase(blockType.getId())) return;
            PlayerRef player = store.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
            if (player == null) return;
            stats.increment(player.getUuid(), StatsManager.CATEGORY_MINED, blockType.getId());
        }
    }

    private static final class BlockPlaceStatsSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final StatsManager stats;

        private BlockPlaceStatsSystem(@Nonnull StatsManager stats) {
            super(PlaceBlockEvent.class);
            this.stats = stats;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
            return Collections.singleton(RootDependency.first());
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull PlaceBlockEvent event) {
            if (!stats.isEnabled() || event.isCancelled() || event.getItemInHand() == null) return;
            Item item = event.getItemInHand().getItem();
            if (item == null) return;
            String id = item.getBlockId() != null ? item.getBlockId() : item.getId();
            if (id == null || id.isBlank() || "Empty".equalsIgnoreCase(id)) return;
            PlayerRef player = store.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
            if (player == null) return;
            stats.increment(player.getUuid(), StatsManager.CATEGORY_PLACED, id);
        }
    }

    private static final class CraftStatsSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {
        private final StatsManager stats;

        private CraftStatsSystem(@Nonnull StatsManager stats) {
            super(CraftRecipeEvent.Post.class);
            this.stats = stats;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
            return Collections.singleton(RootDependency.first());
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull CraftRecipeEvent.Post event) {
            if (!stats.isEnabled() || event.getCraftedRecipe() == null || event.getCraftedRecipe().getPrimaryOutput() == null) return;
            PlayerRef player = store.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
            if (player == null) return;
            String itemId = event.getCraftedRecipe().getPrimaryOutput().getItemId();
            if (itemId == null || itemId.isBlank()) return;
            long quantity = (long) event.getCraftedRecipe().getPrimaryOutput().getQuantity() * Math.max(1L, event.getQuantity());
            stats.increment(player.getUuid(), "crafted", itemId, quantity);
        }
    }

    private static final class DropStatsSystem extends EntityEventSystem<EntityStore, DropItemEvent.Drop> {
        private final StatsManager stats;

        private DropStatsSystem(@Nonnull StatsManager stats) {
            super(DropItemEvent.Drop.class);
            this.stats = stats;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
            return Collections.singleton(RootDependency.first());
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> chunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer,
                           @Nonnull DropItemEvent.Drop event) {
            if (!stats.isEnabled() || event.isCancelled() || event.getItemStack() == null) return;
            PlayerRef player = store.getComponent(chunk.getReferenceTo(index), PlayerRef.getComponentType());
            if (player == null) return;
            ItemStack stack = event.getItemStack();
            if (ItemStack.isEmpty(stack) || stack.getItemId() == null || stack.getItemId().isBlank()) return;
            stats.increment(player.getUuid(), "dropped", stack.getItemId(), stack.getQuantity());
            stats.increment(player.getUuid(), StatsManager.CATEGORY_CUSTOM, "drops");
        }
    }

    private static final class PickupStatsSystem extends HolderSystem<EntityStore> {
        private final StatsManager stats;

        private PickupStatsSystem(@Nonnull StatsManager stats) {
            this.stats = stats;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PickupItemComponent.getComponentType();
        }

        @Override
        public void onEntityAdd(@Nonnull Holder<EntityStore> holder,
                                @Nonnull AddReason reason,
                                @Nonnull Store<EntityStore> store) {
            if (!stats.isEnabled() || reason != AddReason.SPAWN) return;
            PickupItemComponent pickup = holder.getComponent(PickupItemComponent.getComponentType());
            ItemComponent item = holder.getComponent(ItemComponent.getComponentType());
            if (pickup == null || item == null || item.getItemStack() == null) return;
            Ref<EntityStore> targetRef = pickup.getTargetRef();
            if (targetRef == null || !targetRef.isValid()) return;
            PlayerRef player = store.getComponent(targetRef, PlayerRef.getComponentType());
            if (player == null) return;
            ItemStack stack = item.getItemStack();
            if (ItemStack.isEmpty(stack) || stack.getItemId() == null || stack.getItemId().isBlank()) return;
            stats.increment(player.getUuid(), "picked_up", stack.getItemId(), stack.getQuantity());
        }

        @Override
        public void onEntityRemoved(@Nonnull Holder<EntityStore> holder,
                                    @Nonnull RemoveReason reason,
                                    @Nonnull Store<EntityStore> store) {
        }
    }

    private static final class DamageStatsSystem extends DamageEventSystem {
        private final StatsManager stats;
        private final ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage;

        private DamageStatsSystem(@Nonnull StatsManager stats,
                                  @Nonnull ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage) {
            this.stats = stats;
            this.lastDamage = lastDamage;
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
                           @Nonnull Damage event) {
            if (!stats.isEnabled() || event.isCancelled() || event.getAmount() <= 0f) return;

            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            PlayerRef victim = store.getComponent(victimRef, PlayerRef.getComponentType());
            Ref<EntityStore> attackerRef = resolveAttackerRef(event);
            UUID attackerId = null;
            String attackerName = null;
            if (attackerRef != null && attackerRef.isValid()) {
                Store<EntityStore> attackerStore = attackerRef.getStore();
                if (attackerStore != null) {
                    PlayerRef attacker = attackerStore.getComponent(attackerRef, PlayerRef.getComponentType());
                    if (attacker != null) {
                        attackerId = attacker.getUuid();
                        attackerName = "Player";
                        stats.increment(attackerId, StatsManager.CATEGORY_CUSTOM, "damage_dealt", Math.round(event.getAmount()));
                    } else {
                        attackerName = resolveEntityName(attackerRef, attackerStore);
                    }
                }
            }

            if (victim != null) {
                stats.increment(victim.getUuid(), StatsManager.CATEGORY_CUSTOM, "damage_taken", Math.round(event.getAmount()));
                lastDamage.put(victimRef, new DamageRecord(attackerId, attackerName, resolveDamageCause(event), System.currentTimeMillis()));
            } else if (attackerId != null) {
                lastDamage.put(victimRef, new DamageRecord(attackerId, attackerName, resolveDamageCause(event), System.currentTimeMillis()));
            }
        }
    }

    private static final class DeathStatsSystem extends RefChangeSystem<EntityStore, DeathComponent> {
        private final StatsManager stats;
        private final ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage;

        private DeathStatsSystem(@Nonnull StatsManager stats,
                                 @Nonnull ConcurrentHashMap<Ref<EntityStore>, DamageRecord> lastDamage) {
            this.stats = stats;
            this.lastDamage = lastDamage;
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
            if (!stats.isEnabled()) return;
            PlayerRef decedent = store.getComponent(ref, PlayerRef.getComponentType());
            DamageRecord record = lastDamage.remove(ref);
            if (record != null && record.isExpired()) {
                record = null;
            }

            if (decedent != null) {
                stats.increment(decedent.getUuid(), StatsManager.CATEGORY_CUSTOM, "deaths");
                String killer = record != null && record.attackerName() != null ? record.attackerName()
                        : record != null ? record.cause()
                        : "unknown";
                stats.increment(decedent.getUuid(), StatsManager.CATEGORY_KILLED_BY, killer);
            } else if (record != null && record.attackerId() != null) {
                String killed = resolveEntityName(ref, store);
                stats.increment(record.attackerId(), StatsManager.CATEGORY_CUSTOM,
                        "Player".equals(killed) ? "player_kills" : "mob_kills");
                stats.increment(record.attackerId(), StatsManager.CATEGORY_KILLED, killed);
            }
        }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull DeathComponent component,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> buffer) {
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref,
                                   @Nonnull DeathComponent oldComponent,
                                   @Nonnull DeathComponent newComponent,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull CommandBuffer<EntityStore> buffer) {
        }

        @Override
        public com.hypixel.hytale.component.ComponentType<EntityStore, DeathComponent> componentType() {
            return DeathComponent.getComponentType();
        }
    }

    private static final class MovementStatsSystem extends EntityTickingSystem<EntityStore> {
        private static final double TELEPORT_THRESHOLD_BLOCKS = 100.0D;
        private static final double MIN_DISTANCE_BLOCKS = 0.0001D;

        private final StatsManager stats;
        private final ConcurrentHashMap<UUID, Vector3d> lastPositions;
        private final ConcurrentHashMap<UUID, Double> accumulatedDistanceCm;

        private MovementStatsSystem(@Nonnull StatsManager stats,
                                    @Nonnull ConcurrentHashMap<UUID, Vector3d> lastPositions,
                                    @Nonnull ConcurrentHashMap<UUID, Double> accumulatedDistanceCm) {
            this.stats = stats;
            this.lastPositions = lastPositions;
            this.accumulatedDistanceCm = accumulatedDistanceCm;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void tick(float deltaTime,
                         int index,
                         @Nonnull ArchetypeChunk<EntityStore> chunk,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull CommandBuffer<EntityStore> buffer) {
            if (!stats.isEnabled() || !stats.shouldTrackMovement()) return;
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player == null) return;
            Transform transform = player.getTransform();
            if (transform == null || transform.getPosition() == null) return;

            UUID playerId = player.getUuid();
            Vector3d current = new Vector3d(transform.getPosition());
            Vector3d previous = lastPositions.put(playerId, current);
            if (previous == null) return;

            double distance = previous.distance(current);
            if (distance < MIN_DISTANCE_BLOCKS || distance > TELEPORT_THRESHOLD_BLOCKS) return;
            double accumulated = accumulatedDistanceCm.getOrDefault(playerId, 0.0D) + (distance * 100.0D);
            if (accumulated >= 1.0D) {
                long centimeters = (long) accumulated;
                accumulatedDistanceCm.put(playerId, accumulated - centimeters);
                stats.increment(playerId, StatsManager.CATEGORY_CUSTOM, "distance_traveled", centimeters);
            } else {
                accumulatedDistanceCm.put(playerId, accumulated);
            }
        }
    }

    @Nullable
    private static Ref<EntityStore> resolveAttackerRef(@Nonnull Damage event) {
        Damage.Source source = event.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            return entitySource.getRef();
        }
        return null;
    }

    @Nonnull
    private static String resolveEntityName(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (store.getComponent(ref, PlayerRef.getComponentType()) != null) {
            return "Player";
        }
        ModelComponent model = store.getComponent(ref, ModelComponent.getComponentType());
        if (model != null && model.getModel() != null) {
            String id = model.getModel().getModelAssetId();
            if (id == null || id.isBlank()) {
                id = model.getModel().getModel();
            }
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return "unknown";
    }

    @Nonnull
    private static String resolveDamageCause(@Nonnull Damage event) {
        Damage.Source source = event.getSource();
        if (source == null) {
            return "unknown";
        }
        if (source instanceof Damage.EnvironmentSource environmentSource) {
            return "hystats__environment_damage_" + environmentSource.getType();
        }
        return source.getClass().getSimpleName();
    }

    private record DamageRecord(@Nullable UUID attackerId,
                                @Nullable String attackerName,
                                @Nonnull String cause,
                                long timeMs) {
        private boolean isExpired() {
            return System.currentTimeMillis() - timeMs > DAMAGE_TRACK_TIMEOUT_MS;
        }
    }
}
