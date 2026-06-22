package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcNameplateUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ShopNpcFixTask {

    private final ShopManager shopManager;
    private ScheduledFuture<?> initTask;
    private ScheduledFuture<?> periodicTask;

    public ShopNpcFixTask(@Nonnull ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    public void start() {
        stop();
        initTask = HytaleServer.SCHEDULED_EXECUTOR.schedule(this::initialize, 5L, TimeUnit.SECONDS);
        periodicTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::fixAll, 10L, 2L, TimeUnit.SECONDS);
    }

    public void stop() {
        if (initTask != null) {
            initTask.cancel(false);
            initTask = null;
        }
        if (periodicTask != null) {
            periodicTask.cancel(false);
            periodicTask = null;
        }
    }

    private void initialize() {
        try {
            fixAll();
        } catch (Exception e) {
            Log.warn("[ShopNPC] Init failed: " + e.getMessage());
        }
    }

    private void fixAll() {
        List<ShopNpcModel> npcs = shopManager.listAllNpcs();
        if (npcs.isEmpty()) {
            return;
        }
        java.util.Set<java.util.UUID> knownIds = new java.util.HashSet<>();
        for (ShopNpcModel npc : npcs) {
            String id = npc.getNpcId();
            if (id.isBlank()) continue;
            try {
                knownIds.add(java.util.UUID.fromString(id));
            } catch (Exception ignored) {
            }
        }
        for (World world : Universe.get().getWorlds().values()) {
            world.execute(() -> fixWorld(world, npcs, knownIds));
        }
    }

    private void fixWorld(@Nonnull World world,
                          @Nonnull List<ShopNpcModel> npcs,
                          @Nonnull java.util.Set<java.util.UUID> knownIds) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (ShopNpcModel npc : npcs) {
            try {
                String id = npc.getNpcId();
                if (id.isBlank()) continue;
                UUID npcUuid = UUID.fromString(id);
                findAndFixNpc(world, store, npcUuid, npc, knownIds);
            } catch (Exception ignored) {
            }
        }
    }

    private void findAndFixNpc(@Nonnull World world,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull UUID npcUuid,
                               @Nonnull ShopNpcModel loc,
                               @Nonnull java.util.Set<java.util.UUID> knownIds) {
        Vector3i blockPos = loc.getPosition();
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null) {
                    return;
                }
                boolean matched = false;
                try {
                    matched = npc.getUuid().equals(npcUuid);
                } catch (Exception ignored) {
                }
                if (!matched) {
                    TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        Vector3d pos = transform.getPosition();
                        double dx = Math.abs(pos.getX() - (blockPos.getX() + 0.5D));
                        double dy = Math.abs(pos.getY() - blockPos.getY());
                        double dz = Math.abs(pos.getZ() - (blockPos.getZ() + 0.5D));
                        matched = dx < 1.5D && dy < 2.0D && dz < 1.5D;
                        if (matched) {
                            UUID currentId = null;
                            try {
                                currentId = npc.getUuid();
                            } catch (Exception ignored) {
                            }
                            if (currentId != null && knownIds.contains(currentId)) {
                                matched = currentId.equals(npcUuid);
                            }
                        }
                    }
                }
                if (matched) {
                    world.execute(() -> applyFixes(world, store, ref, npc, loc));
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
            Vector3d leashPos = new Vector3d(blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D);
            npc.setLeashPoint(leashPos);
            TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
            if (transform != null && transform.getRotation() != null) {
                npc.setLeashHeading(transform.getRotation().getYaw());
            }
            ShopNpcInteractionRegistry.applyNpcInteractions(store, npcRef);
        } catch (Exception ignored) {
        }
    }
}

