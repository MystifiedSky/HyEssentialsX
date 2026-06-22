package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerCompatUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcEntityUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcNameplateUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ShopNpcFixTask {

    private final ShopManager shopManager;
    private ScheduledFuture<?> bootstrapTask;
    private ScheduledFuture<?> periodicTask;

    public ShopNpcFixTask(@Nonnull ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    public void start() {
        stop();
        AtomicInteger bootstrapRuns = new AtomicInteger();
        bootstrapTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            safeFixAll("bootstrap");
            if (bootstrapRuns.incrementAndGet() >= 30) {
                cancelBootstrap();
            }
        }, 1L, 2L, TimeUnit.SECONDS);
        periodicTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> safeFixAll("periodic"),
                30L,
                30L,
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        cancelBootstrap();
        if (periodicTask != null) {
            periodicTask.cancel(false);
            periodicTask = null;
        }
    }

    private void cancelBootstrap() {
        if (bootstrapTask != null) {
            bootstrapTask.cancel(false);
            bootstrapTask = null;
        }
    }

    private void safeFixAll(@Nonnull String phase) {
        try {
            fixAll();
        } catch (Exception e) {
            Log.warn("[ShopNPC] " + phase + " fix failed: " + e.getMessage());
        }
    }

    private void fixAll() {
        List<ShopNpcModel> npcs = shopManager.listAllNpcs();
        if (npcs.isEmpty()) {
            return;
        }
        java.util.Set<java.util.UUID> knownIds = new java.util.HashSet<>();
        java.util.Set<String> shopNames = new java.util.HashSet<>();
        for (ShopNpcModel npc : npcs) {
            String id = npc.getNpcId();
            if (!id.isBlank()) {
                try {
                    knownIds.add(java.util.UUID.fromString(id));
                } catch (Exception ignored) {
                }
            }
            ShopModel shop = shopManager.getShop(npc.getShopName());
            if (shop != null) {
                shopNames.add(shop.getName().toLowerCase(java.util.Locale.ROOT));
                shopNames.add(shop.getDisplayName().toLowerCase(java.util.Locale.ROOT));
            } else if (!npc.getShopName().isBlank()) {
                shopNames.add(npc.getShopName().toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> fixWorld(world, npcs, knownIds, shopNames));
        }
    }

    private void fixWorld(@Nonnull World world,
                          @Nonnull List<ShopNpcModel> npcs,
                          @Nonnull java.util.Set<java.util.UUID> knownIds,
                          @Nonnull java.util.Set<String> shopNames) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        java.util.Set<Ref<EntityStore>> protectedRefs = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Queue<NpcFixTarget> targets = new ConcurrentLinkedQueue<>();
        for (ShopNpcModel npc : npcs) {
            try {
                if (!npc.getWorldId().equalsIgnoreCase(world.getName())) {
                    continue;
                }
                findAndFixNpc(store, parseUuid(npc.getNpcId()), npc, protectedRefs, targets);
            } catch (Exception ignored) {
            }
        }
        for (NpcFixTarget target : targets) {
            applyFixes(world, store, target.ref, target.npc, target.loc);
        }
        removeLegacyShopInteractionOverrides(store);
        removeOrphanedShopNpcEntities(store, knownIds, shopNames, protectedRefs);
    }

    private void findAndFixNpc(@Nonnull Store<EntityStore> store,
                               UUID npcUuid,
                               @Nonnull ShopNpcModel loc,
                               @Nonnull java.util.Set<Ref<EntityStore>> protectedRefs,
                               @Nonnull Queue<NpcFixTarget> targets) {
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null) {
                    return;
                }
                if (!matchesStoredShopNpc(store, ref, npc, npcUuid, loc)) {
                    return;
                }
                if (protectedRefs.add(ref)) {
                    targets.add(new NpcFixTarget(ref, npc, loc));
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void applyFixes(@Nonnull World world,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> npcRef,
                            @Nonnull NPCEntity npc,
                            @Nonnull ShopNpcModel loc) {
        try {
            if (store.getComponent(npcRef, Invulnerable.getComponentType()) == null) {
                store.addComponent(npcRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            }
            if (store.getComponent(npcRef, Interactable.getComponentType()) == null) {
                store.addComponent(npcRef, Interactable.getComponentType(), Interactable.INSTANCE);
            }
            if (store.getComponent(npcRef, Frozen.getComponentType()) == null) {
                store.addComponent(npcRef, Frozen.getComponentType(), Frozen.get());
            }
            String displayName = loc.getShopName();
            ShopModel shop = shopManager.getShop(loc.getShopName());
            if (shop != null) {
                displayName = shop.getDisplayName();
            }
            ShopNpcNameplateUtil.apply(store, npcRef, displayName);
            MovementStatesComponent movementStates = store.getComponent(npcRef, MovementStatesComponent.getComponentType());
            if (movementStates != null) {
                MovementStates states = movementStates.getMovementStates();
                states.idle = true;
                states.horizontalIdle = true;
                states.walking = false;
                states.running = false;
                states.sprinting = false;
                states.onGround = true;
            }
            npc.setDespawnTime(Float.MAX_VALUE);
            npc.setDespawning(false);
            Vector3i blockPos = loc.getPosition();
            Vector3d leashPos = new Vector3d(blockPos.x() + 0.5D, blockPos.y(), blockPos.z() + 0.5D);
            npc.setLeashPoint(leashPos);
            TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
            if (transform != null && transform.getRotation() != null) {
                npc.setLeashHeading(transform.getRotation().yaw());
            }
            refreshStoredNpcId(npc, loc);
            ShopNpcInteractionRegistry.applyNpcInteractions(store, npcRef);
        } catch (Exception ignored) {
        }
    }

    private void removeOrphanedShopNpcEntities(@Nonnull Store<EntityStore> store,
                                               @Nonnull java.util.Set<java.util.UUID> knownIds,
                                               @Nonnull java.util.Set<String> shopNames,
                                               @Nonnull java.util.Set<Ref<EntityStore>> protectedRefs) {
        if (shopNames.isEmpty()) {
            return;
        }
        Queue<Ref<EntityStore>> toRemove = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                if (protectedRefs.contains(ref)) {
                    return;
                }
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null) return;

                UUID currentId = ServerCompatUtil.getUuid(npc);
                if (currentId != null && knownIds.contains(currentId)) {
                    return;
                }

                if (!ShopNpcEntityUtil.matchesAnyShopName(store, ref, shopNames)) {
                    return;
                }

                toRemove.add(ref);
            } catch (Exception ignored) {
            }
        });
        for (Ref<EntityStore> ref : toRemove) {
            try {
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null) {
                    npc.setToDespawn();
                    npc.setDespawning(true);
                    npc.setDespawnTime(0f);
                    npc.setDespawnRemainingSeconds(0f);
                    npc.setDespawnCheckRemainingSeconds(0f);
                }
                store.removeEntity(ref, RemoveReason.REMOVE);
            } catch (Exception ignored) {
            }
        }
    }

    private void removeLegacyShopInteractionOverrides(@Nonnull Store<EntityStore> store) {
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
                if (interactions == null) {
                    return;
                }
                String interactionId = interactions.getInteractionId(InteractionType.Use);
                String hint = interactions.getInteractionHint();
                boolean oldShopInteraction = interactionId != null
                        && ShopNpcInteractionRegistry.ADMIN_SHOP_ROOT_INTERACTION_ID.equalsIgnoreCase(interactionId);
                boolean oldShopHint = hint != null
                        && hint.toLowerCase(java.util.Locale.ROOT).contains("open shop");
                if (!oldShopInteraction && !oldShopHint) {
                    return;
                }
                commandBuffer.tryRemoveComponent(ref, Interactions.getComponentType());
                if (store.getComponent(ref, Interactable.getComponentType()) == null) {
                    commandBuffer.addComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private UUID parseUuid(@Nonnull String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean matchesStoredShopNpc(@Nonnull Store<EntityStore> store,
                                         @Nonnull Ref<EntityStore> ref,
                                         @Nonnull NPCEntity npc,
                                         UUID npcUuid,
                                         @Nonnull ShopNpcModel loc) {
        if (npcUuid != null) {
            try {
                if (npcUuid.equals(ServerCompatUtil.getUuid(npc))) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        java.util.Set<String> names = shopNamesFor(loc);
        if (!ShopNpcEntityUtil.matchesAnyShopName(store, ref, names)) {
            return false;
        }
        return distanceSquared(store, ref, loc.getPosition()) <= 9.0D;
    }

    private java.util.Set<String> shopNamesFor(@Nonnull ShopNpcModel loc) {
        java.util.Set<String> names = new java.util.HashSet<>();
        ShopModel shop = shopManager.getShop(loc.getShopName());
        if (shop != null) {
            if (!shop.getName().isBlank()) {
                names.add(shop.getName().toLowerCase(java.util.Locale.ROOT));
            }
            if (!shop.getDisplayName().isBlank()) {
                names.add(shop.getDisplayName().toLowerCase(java.util.Locale.ROOT));
            }
        } else if (!loc.getShopName().isBlank()) {
            names.add(loc.getShopName().toLowerCase(java.util.Locale.ROOT));
        }
        return names;
    }

    private double distanceSquared(@Nonnull Store<EntityStore> store,
                                   @Nonnull Ref<EntityStore> ref,
                                   @Nonnull Vector3i blockPos) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return Double.MAX_VALUE / 2.0D;
        }
        Vector3d pos = transform.getPosition();
        double dx = pos.x() - (blockPos.x() + 0.5D);
        double dy = pos.y() - blockPos.y();
        double dz = pos.z() - (blockPos.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private void refreshStoredNpcId(@Nonnull NPCEntity npc, @Nonnull ShopNpcModel loc) {
        try {
            UUID currentId = ServerCompatUtil.getUuid(npc);
            if (currentId == null || loc.getNpcId().equalsIgnoreCase(currentId.toString())) {
                return;
            }
            ShopModel shop = shopManager.getShop(loc.getShopName());
            if (shop == null) {
                return;
            }
            loc.setNpcId(currentId.toString());
            shopManager.saveShop(shop);
        } catch (Exception ignored) {
        }
    }

    private static final class NpcFixTarget {
        private final Ref<EntityStore> ref;
        private final NPCEntity npc;
        private final ShopNpcModel loc;

        private NpcFixTarget(@Nonnull Ref<EntityStore> ref,
                             @Nonnull NPCEntity npc,
                             @Nonnull ShopNpcModel loc) {
            this.ref = ref;
            this.npc = npc;
            this.loc = loc;
        }
    }
}

