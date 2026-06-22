package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector3d;
import org.joml.Vector3i;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ShopNpcEntityUtil {

    private static final Map<String, Ref<EntityStore>> NPC_REF_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LIFECYCLE_LOCKS = ConcurrentHashMap.newKeySet();
    private static final String[] PREFERRED_ROLES = {
            "Klops_Merchant",
            "Feran_Civilian",
            "Kweebec_Civilian",
            "Trork_Civilian",
            "Human_Civilian"
    };

    private ShopNpcEntityUtil() {
    }

    public static boolean moveRegisteredNpc(@Nonnull World world,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull ShopNpcModel npcModel,
                                            @Nonnull Vector3d position,
                                            @Nonnull com.hypixel.hytale.math.vector.Rotation3f rotation,
                                            @Nonnull Vector3i basePos,
                                            @Nonnull String displayName) {
        Ref<EntityStore> npcRef = resolveRefByModel(world, store, npcModel);
        if (npcRef == null) {
            return false;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return false;
        }
        moveNpc(store, npcRef, npc, position, rotation, basePos, displayName);
        npcModel.setPosition(basePos);
        npcModel.setWorldId(world.getName());
        registerRef(npcModel.getShopName(), world.getName(), npcRef);
        return true;
    }

    @Nonnull
    public static NpcLifecycleResult moveOrSpawnShopNpc(@Nonnull World world,
                                                        @Nonnull Store<EntityStore> store,
                                                        @Nonnull ShopModel shop,
                                                        @Nullable ShopNpcModel preferredNpc,
                                                        @Nonnull Vector3d position,
                                                        @Nonnull com.hypixel.hytale.math.vector.Rotation3f rotation,
                                                        @Nonnull Vector3i basePos,
                                                        @Nonnull String displayName,
                                                        @Nonnull String spawnerUuid,
                                                        @Nonnull String spawnerName,
                                                        @Nonnull String roleName,
                                                        int roleIndex) {
        String worldName = world.getName();
        String key = cacheKey(shop.getName(), worldName);
        if (!LIFECYCLE_LOCKS.add(key)) {
            return NpcLifecycleResult.busyResult();
        }
        try {
            ShopNpcModel existingModel = preferredNpc != null ? preferredNpc : firstNpcInWorld(shop, worldName);
            Ref<EntityStore> existingRef = resolveShopNpcRef(world, store, shop, existingModel, basePos);
            if (existingRef != null) {
                NPCEntity existingNpc = store.getComponent(existingRef, NPCEntity.getComponentType());
                if (existingNpc != null) {
                    moveNpc(store, existingRef, existingNpc, position, rotation, basePos, displayName);
                    UUID npcUuid = ServerCompatUtil.getUuid(existingNpc);
                    if (npcUuid == null) {
                        return NpcLifecycleResult.failed("existing npc uuid missing");
                    }
                    ShopNpcModel updated = replaceWorldNpcModel(
                            shop,
                            worldName,
                            npcUuid.toString(),
                            basePos,
                            spawnerUuid,
                            spawnerName,
                            roleName
                    );
                    registerRef(shop.getName(), worldName, existingRef);
                    int removed = removeDuplicateShopNpcs(store, shop, worldName, updated.getPosition(), npcUuid.toString());
                    if (removed > 0) {
                        Log.info("[ShopNPC] Removed " + removed + " duplicate NPC(s) for " + shop.getName() + " in " + worldName);
                    }
                    return NpcLifecycleResult.moved(npcUuid.toString(), removed);
                }
            }

            int preRemoved = removeNamedShopNpcsInWorld(store, shop, "");
            if (preRemoved > 0) {
                Log.info("[ShopNPC] Removed " + preRemoved + " stale named NPC(s) before spawning " + shop.getName() + " in " + worldName);
            }

            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null || roleIndex < 0) {
                return NpcLifecycleResult.failed("npc plugin unavailable");
            }
            Pair<Ref<EntityStore>, NPCEntity> npcPair =
                    npcPlugin.spawnEntity(store, roleIndex, position, rotation, null, null);
            if (npcPair == null) {
                return NpcLifecycleResult.failed("spawn failed");
            }
            Ref<EntityStore> npcRef = npcPair.first();
            NPCEntity spawnedNpc = npcPair.second();
            applyStableShopNpcComponents(store, npcRef);
            moveNpc(store, npcRef, spawnedNpc, position, rotation, basePos, displayName);
            ShopNpcInteractionRegistry.applyNpcInteractions(store, npcRef);

            UUID spawnedNpcId = ServerCompatUtil.getUuid(spawnedNpc);
            if (spawnedNpcId == null) {
                removeRef(store, npcRef);
                return NpcLifecycleResult.failed("spawned npc uuid missing");
            }
            replaceWorldNpcModel(
                    shop,
                    worldName,
                    spawnedNpcId.toString(),
                    basePos,
                    spawnerUuid,
                    spawnerName,
                    roleName
            );
            registerRef(shop.getName(), worldName, npcRef);
            int removed = preRemoved + removeDuplicateShopNpcs(store, shop, worldName, basePos, spawnedNpcId.toString());
            return NpcLifecycleResult.spawned(spawnedNpcId.toString(), removed);
        } finally {
            LIFECYCLE_LOCKS.remove(key);
        }
    }

    @Nonnull
    public static NpcLifecycleResult replaceNpcRole(@Nonnull World world,
                                                    @Nonnull Store<EntityStore> store,
                                                    @Nonnull ShopModel shop,
                                                    @Nonnull ShopNpcModel npcModel,
                                                    int roleIndex,
                                                    @Nonnull Vector3d position,
                                                    @Nonnull com.hypixel.hytale.math.vector.Rotation3f rotation,
                                                    @Nonnull String displayName) {
        String worldName = world.getName();
        String key = cacheKey(shop.getName(), worldName);
        if (!LIFECYCLE_LOCKS.add(key)) {
            return NpcLifecycleResult.busyResult();
        }
        try {
            Ref<EntityStore> existingRef = resolveShopNpcRef(world, store, shop, npcModel, npcModel.getPosition());
            int removed = 0;
            if (existingRef != null) {
                removed += removeRef(store, existingRef);
            }
            removed += removeAllMatchingShopNpcs(store, shop, npcModel);
            unregisterRef(shop.getName(), worldName);

            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null || roleIndex < 0) {
                return NpcLifecycleResult.failed("npc plugin unavailable");
            }
            Pair<Ref<EntityStore>, NPCEntity> npcPair =
                    npcPlugin.spawnEntity(store, roleIndex, position, rotation, null, null);
            if (npcPair == null) {
                return NpcLifecycleResult.failed("spawn failed");
            }
            Ref<EntityStore> npcRef = npcPair.first();
            NPCEntity spawnedNpc = npcPair.second();
            applyStableShopNpcComponents(store, npcRef);
            moveNpc(store, npcRef, spawnedNpc, position, rotation, npcModel.getPosition(), displayName);
            ShopNpcInteractionRegistry.applyNpcInteractions(store, npcRef);
            UUID spawnedNpcId = ServerCompatUtil.getUuid(spawnedNpc);
            if (spawnedNpcId == null) {
                removeRef(store, npcRef);
                return NpcLifecycleResult.failed("spawned npc uuid missing");
            }
            npcModel.setNpcId(spawnedNpcId.toString());
            npcModel.setWorldId(worldName);
            npcModel.setRoleName(shop.getNpcRole());
            registerRef(shop.getName(), worldName, npcRef);
            removed += removeDuplicateShopNpcs(store, shop, worldName, npcModel.getPosition(), spawnedNpcId.toString());
            if (removed > 0) {
                Log.info("[ShopNPC] Replaced role for " + shop.getName() + " in " + worldName + " and removed " + removed + " old NPC ref(s)");
            }
            return NpcLifecycleResult.spawned(spawnedNpcId.toString(), removed);
        } finally {
            LIFECYCLE_LOCKS.remove(key);
        }
    }

    @Nonnull
    public static String reconcileAndMoveSingleNpc(@Nonnull World world,
                                                   @Nonnull Store<EntityStore> store,
                                                   @Nonnull ShopModel shop,
                                                   @Nonnull Vector3d position,
                                                   @Nonnull com.hypixel.hytale.math.vector.Rotation3f rotation,
                                                   @Nonnull Vector3i basePos,
                                                   @Nonnull String displayName) {
        ShopNpcModel current = firstNpcInWorld(shop, world.getName());
        Ref<EntityStore> keep = resolveShopNpcRef(world, store, shop, current, basePos);
        if (keep == null) {
            return "";
        }
        NPCEntity keepNpc = store.getComponent(keep, NPCEntity.getComponentType());
        if (keepNpc == null) {
            return "";
        }
        moveNpc(store, keep, keepNpc, position, rotation, basePos, displayName);
        UUID keepId = ServerCompatUtil.getUuid(keepNpc);
        if (keepId == null) {
            return "";
        }
        if (current != null) {
            current.setNpcId(keepId.toString());
            current.setPosition(basePos);
            current.setWorldId(world.getName());
        }
        registerRef(shop.getName(), world.getName(), keep);
        removeDuplicateShopNpcs(store, shop, world.getName(), basePos, keepId.toString());
        return keepId.toString();
    }

    public static boolean hasExistingShopNpc(@Nonnull World world,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull ShopModel shop) {
        return resolveShopNpcRef(world, store, shop, firstNpcInWorld(shop, world.getName()), null) != null;
    }

    public static boolean removeRegisteredOrStaleNpc(@Nonnull Store<EntityStore> store,
                                                     @Nonnull ShopModel shop,
                                                     @Nonnull ShopNpcModel npcModel) {
        return removeAllMatchingShopNpcs(store, shop, npcModel) > 0;
    }

    @Nullable
    public static RoleSelection resolveRoleSelection(@Nonnull String preferredRole) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            return null;
        }
        List<String> availableRoles = npcPlugin.getRoleTemplateNames(true);
        if (!preferredRole.isBlank() && availableRoles.contains(preferredRole)) {
            int index = npcPlugin.getIndex(preferredRole);
            if (index >= 0) {
                return new RoleSelection(preferredRole, index);
            }
        }
        for (String preferred : PREFERRED_ROLES) {
            if (!availableRoles.contains(preferred)) {
                continue;
            }
            int index = npcPlugin.getIndex(preferred);
            if (index >= 0) {
                return new RoleSelection(preferred, index);
            }
        }
        for (String role : availableRoles) {
            int index = npcPlugin.getIndex(role);
            if (index >= 0) {
                return new RoleSelection(role, index);
            }
        }
        return null;
    }

    public static int removeDuplicateShopNpcs(@Nonnull Store<EntityStore> store,
                                              @Nonnull ShopModel shop,
                                              @Nonnull String worldName,
                                              @Nonnull Vector3i keepPosition,
                                              @Nonnull String keepNpcId) {
        return removeNamedShopNpcsInWorld(store, shop, keepNpcId);
    }

    public static int removeAllMatchingShopNpcs(@Nonnull Store<EntityStore> store,
                                                @Nonnull ShopModel shop,
                                                @Nonnull ShopNpcModel npcModel) {
        Queue<Ref<EntityStore>> toRemove = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null || !matchesShopNpc(store, ref, npc, shop, npcModel)) {
                    return;
                }
                toRemove.add(ref);
            } catch (Exception ignored) {
            }
        });
        return removeRefs(store, toRemove);
    }

    public static int removeAllShopNpcsInWorld(@Nonnull Store<EntityStore> store,
                                               @Nonnull ShopModel shop) {
        return removeNamedShopNpcsInWorld(store, shop, "");
    }

    public static int removeShopNpcsByName(@Nonnull Store<EntityStore> store,
                                           @Nonnull String displayName,
                                           @Nonnull String rawName) {
        Set<String> names = new HashSet<>();
        if (!displayName.isBlank()) {
            names.add(displayName.toLowerCase(Locale.ROOT));
        }
        if (!rawName.isBlank()) {
            names.add(rawName.toLowerCase(Locale.ROOT));
        }
        Queue<Ref<EntityStore>> toRemove = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null && matchesName(store, ref, names)) {
                    toRemove.add(ref);
                }
            } catch (Exception ignored) {
            }
        });
        return removeRefs(store, toRemove);
    }

    public static int countMatchingShopNpcs(@Nonnull Store<EntityStore> store,
                                            @Nonnull ShopModel shop,
                                            @Nonnull ShopNpcModel npcModel) {
        AtomicInteger count = new AtomicInteger(0);
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null && matchesShopNpc(store, ref, npc, shop, npcModel)) {
                    count.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        });
        return count.get();
    }

    public static boolean matchesAnyShopName(@Nonnull Store<EntityStore> store,
                                             @Nonnull Ref<EntityStore> ref,
                                             @Nonnull Set<String> names) {
        return matchesName(store, ref, names);
    }

    private static int removeNamedShopNpcsInWorld(@Nonnull Store<EntityStore> store,
                                                  @Nonnull ShopModel shop,
                                                  @Nonnull String keepNpcId) {
        Set<String> names = shopNames(shop);
        Queue<Ref<EntityStore>> toRemove = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null || !matchesName(store, ref, names)) {
                    return;
                }
                UUID uuid = ServerCompatUtil.getUuid(npc);
                String currentId = uuid == null ? "" : uuid.toString();
                if (!keepNpcId.isBlank() && keepNpcId.equalsIgnoreCase(currentId)) {
                    return;
                }
                toRemove.add(ref);
            } catch (Exception ignored) {
            }
        });
        return removeRefs(store, toRemove);
    }

    @Nullable
    private static Ref<EntityStore> resolveShopNpcRef(@Nonnull World world,
                                                      @Nonnull Store<EntityStore> store,
                                                      @Nonnull ShopModel shop,
                                                      @Nullable ShopNpcModel npcModel,
                                                      @Nullable Vector3i fallbackPosition) {
        String worldName = world.getName();
        Ref<EntityStore> cached = NPC_REF_CACHE.get(cacheKey(shop.getName(), worldName));
        if (isValidNpcRef(store, cached)) {
            return cached;
        }
        if (npcModel != null) {
            Ref<EntityStore> byModel = resolveRefByModel(world, store, npcModel);
            if (byModel != null) {
                registerRef(shop.getName(), worldName, byModel);
                return byModel;
            }
        }
        Ref<EntityStore> scanned = findBestNpcRef(store, shop, npcModel, fallbackPosition);
        if (scanned != null) {
            registerRef(shop.getName(), worldName, scanned);
        }
        return scanned;
    }

    @Nullable
    private static Ref<EntityStore> resolveRefByModel(@Nonnull World world,
                                                      @Nonnull Store<EntityStore> store,
                                                      @Nonnull ShopNpcModel npcModel) {
        String npcId = npcModel.getNpcId();
        if (npcId.isBlank()) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(npcId);
        } catch (Exception ignored) {
            return null;
        }

        try {
            Ref<EntityStore> direct = world.getEntityStore().getRefFromUUID(uuid);
            if (isValidNpcRef(store, direct)) {
                return direct;
            }
        } catch (Exception ignored) {
        }

        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            if (found.get() != null) {
                return;
            }
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null && uuid.equals(ServerCompatUtil.getUuid(npc))) {
                    found.set(ref);
                }
            } catch (Exception ignored) {
            }
        });
        return found.get();
    }

    @Nullable
    private static Ref<EntityStore> findBestNpcRef(@Nonnull Store<EntityStore> store,
                                                   @Nonnull ShopModel shop,
                                                   @Nullable ShopNpcModel npcModel,
                                                   @Nullable Vector3i fallbackPosition) {
        Set<String> names = shopNames(shop);
        Set<String> modelIds = new HashSet<>();
        for (ShopNpcModel model : shop.getNpcs()) {
            if (model != null && !model.getNpcId().isBlank()) {
                modelIds.add(model.getNpcId().toLowerCase(Locale.ROOT));
            }
        }
        Vector3i preferredPosition = npcModel != null ? npcModel.getPosition() : fallbackPosition;
        List<NpcCandidate> candidates = new ArrayList<>();
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null) {
                    return;
                }
                UUID uuid = ServerCompatUtil.getUuid(npc);
                String id = uuid == null ? "" : uuid.toString().toLowerCase(Locale.ROOT);
                boolean idMatch = !id.isBlank() && modelIds.contains(id);
                boolean nameMatch = matchesName(store, ref, names);
                if (!idMatch && !nameMatch) {
                    return;
                }
                double distance = preferredPosition == null
                        ? 0.0D
                        : distanceSquared(store, ref, preferredPosition);
                synchronized (candidates) {
                    candidates.add(new NpcCandidate(ref, idMatch, nameMatch, distance));
                }
            } catch (Exception ignored) {
            }
        });
        return chooseKeepRef(candidates);
    }

    @Nullable
    private static Ref<EntityStore> chooseKeepRef(@Nonnull List<NpcCandidate> candidates) {
        Ref<EntityStore> best = null;
        double bestScore = Double.MAX_VALUE;
        for (NpcCandidate candidate : candidates) {
            double score = candidate.distanceSquared();
            if (candidate.idMatch()) {
                score -= 1_000_000.0D;
            } else if (candidate.nameMatch()) {
                score -= 10_000.0D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate.ref();
            }
        }
        return best;
    }

    private static boolean matchesId(@Nonnull NPCEntity npc, @Nonnull String npcId) {
        if (npcId.isBlank()) {
            return false;
        }
        UUID uuid = ServerCompatUtil.getUuid(npc);
        return uuid != null && npcId.equalsIgnoreCase(uuid.toString());
    }

    private static boolean matchesShopNpc(@Nonnull Store<EntityStore> store,
                                          @Nonnull Ref<EntityStore> ref,
                                          @Nonnull NPCEntity npc,
                                          @Nonnull ShopModel shop,
                                          @Nonnull ShopNpcModel npcModel) {
        String npcId = npcModel.getNpcId();
        if (!npcId.isBlank() && matchesId(npc, npcId)) {
            return true;
        }
        return matchesName(store, ref, shopNames(shop));
    }

    private static boolean matchesName(@Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref,
                                       @Nonnull Set<String> names) {
        if (names.isEmpty()) {
            return false;
        }
        Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
        String text = nameplate == null ? null : nameplate.getText();
        if (text != null && containsName(text, names)) {
            return true;
        }
        String displayName = getDisplayNameComponentText(store, ref);
        return displayName != null && containsName(displayName, names);
    }

    private static boolean containsName(@Nonnull String text, @Nonnull Set<String> names) {
        String lowered = text.trim().toLowerCase(Locale.ROOT);
        for (String name : names) {
            if (lowered.equals(name) || lowered.contains(name)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String getDisplayNameComponentText(@Nonnull Store<EntityStore> store,
                                                       @Nonnull Ref<EntityStore> ref) {
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent");
            Method getType = cls.getMethod("getComponentType");
            com.hypixel.hytale.component.ComponentType type =
                    (com.hypixel.hytale.component.ComponentType) getType.invoke(null);
            Object comp = ((Store) store).getComponent(ref, type);
            if (comp == null) {
                return null;
            }
            Object message = getDisplayNameObject(comp);
            if (message == null) {
                return null;
            }
            return messageToText(message);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Object getDisplayNameObject(@Nonnull Object comp) {
        for (String methodName : List.of("getDisplayName", "displayName", "getName")) {
            try {
                Method method = comp.getClass().getMethod(methodName);
                Object value = method.invoke(comp);
                if (value != null) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            Field field = comp.getClass().getDeclaredField("displayName");
            field.setAccessible(true);
            return field.get(comp);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nonnull
    private static String messageToText(@Nonnull Object message) {
        if (message instanceof Message) {
            return message.toString();
        }
        for (String methodName : List.of("getText", "text", "getPlainText", "toPlainText", "getString")) {
            try {
                Method method = message.getClass().getMethod(methodName);
                Object value = method.invoke(message);
                if (value != null) {
                    return value.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return message.toString();
    }

    private static boolean isValidNpcRef(@Nonnull Store<EntityStore> store,
                                         @Nullable Ref<EntityStore> ref) {
        if (ref == null) {
            return false;
        }
        try {
            if (!ref.isValid()) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }
        return store.getComponent(ref, NPCEntity.getComponentType()) != null;
    }

    @Nonnull
    private static ShopNpcModel replaceWorldNpcModel(@Nonnull ShopModel shop,
                                                     @Nonnull String worldName,
                                                     @Nonnull String npcId,
                                                     @Nonnull Vector3i basePos,
                                                     @Nonnull String spawnerUuid,
                                                     @Nonnull String spawnerName,
                                                     @Nonnull String roleName) {
        ShopNpcModel existing = firstNpcInWorld(shop, worldName);
        String storedSpawnerUuid = existing != null && !existing.getSpawnerUuid().isBlank()
                ? existing.getSpawnerUuid()
                : spawnerUuid;
        String storedSpawnerName = existing != null && !existing.getSpawnerName().isBlank()
                ? existing.getSpawnerName()
                : spawnerName;
        String storedRoleName = !roleName.isBlank()
                ? roleName
                : (existing != null && !existing.getRoleName().isBlank() ? existing.getRoleName() : shop.getNpcRole());

        shop.getNpcs().removeIf(npc -> npc != null && npc.getWorldId().equalsIgnoreCase(worldName));
        ShopNpcModel updated = new ShopNpcModel(
                npcId,
                basePos,
                worldName,
                shop.getName(),
                storedSpawnerUuid,
                storedSpawnerName,
                storedRoleName
        );
        shop.getNpcs().add(updated);
        return updated;
    }

    @Nullable
    private static ShopNpcModel firstNpcInWorld(@Nonnull ShopModel shop,
                                                @Nonnull String worldName) {
        for (ShopNpcModel npcModel : shop.getNpcs()) {
            if (npcModel != null && npcModel.getWorldId().equalsIgnoreCase(worldName)) {
                return npcModel;
            }
        }
        return null;
    }

    private static void moveNpc(@Nonnull Store<EntityStore> store,
                                @Nonnull Ref<EntityStore> ref,
                                @Nonnull NPCEntity npc,
                                @Nonnull Vector3d position,
                                @Nonnull com.hypixel.hytale.math.vector.Rotation3f rotation,
                                @Nonnull Vector3i basePos,
                                @Nonnull String displayName) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            transform.setPosition(position);
            transform.setRotation(rotation);
            try {
                transform.markChunkDirty(store);
            } catch (Exception ignored) {
            }
        }
        npc.setLeashPoint(new Vector3d(basePos.x() + 0.5D, basePos.y(), basePos.z() + 0.5D));
        npc.setLeashHeading(rotation.y());
        npc.setDespawning(false);
        npc.setDespawnTime(Float.MAX_VALUE);
        npc.setDespawnRemainingSeconds(Float.MAX_VALUE);
        npc.setDespawnCheckRemainingSeconds(Float.MAX_VALUE);
        ShopNpcNameplateUtil.apply(store, ref, displayName);
        ShopNpcInteractionRegistry.applyNpcInteractions(store, ref);
        applyStableShopNpcComponents(store, ref);
    }

    private static Set<String> shopNames(@Nonnull ShopModel shop) {
        Set<String> names = new HashSet<>();
        if (!shop.getName().isBlank()) {
            names.add(shop.getName().toLowerCase(Locale.ROOT));
        }
        if (!shop.getDisplayName().isBlank()) {
            names.add(shop.getDisplayName().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static double distanceSquared(@Nonnull Store<EntityStore> store,
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

    private static void applyStableShopNpcComponents(@Nonnull Store<EntityStore> store,
                                                     @Nonnull Ref<EntityStore> ref) {
        if (store.getComponent(ref, Interactable.getComponentType()) == null) {
            store.addComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
        }
        if (store.getComponent(ref, Invulnerable.getComponentType()) == null) {
            store.addComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        }
        if (store.getComponent(ref, Frozen.getComponentType()) == null) {
            store.addComponent(ref, Frozen.getComponentType(), Frozen.get());
        }
        MovementStatesComponent movementStates = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (movementStates != null) {
            MovementStates states = movementStates.getMovementStates();
            states.idle = true;
            states.horizontalIdle = true;
            states.walking = false;
            states.running = false;
            states.sprinting = false;
            states.onGround = true;
        }
    }

    private static int removeRefs(@Nonnull Store<EntityStore> store,
                                  @Nonnull Queue<Ref<EntityStore>> refs) {
        int removed = 0;
        Set<Ref<EntityStore>> seen = new HashSet<>();
        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !seen.add(ref)) {
                continue;
            }
            removed += removeRef(store, ref);
        }
        return removed;
    }

    private static int removeRef(@Nonnull Store<EntityStore> store,
                                 @Nonnull Ref<EntityStore> ref) {
        try {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            Ref<EntityStore> removeRef = npc == null ? ref : effectiveRef(npc, ref);
            if (npc != null) {
                markRemoving(npc);
            }
            store.removeEntity(removeRef, RemoveReason.REMOVE);
            unregisterRefByRef(removeRef);
            return 1;
        } catch (Exception first) {
            try {
                store.removeEntity(ref, RemoveReason.REMOVE);
                unregisterRefByRef(ref);
                return 1;
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    @Nonnull
    private static Ref<EntityStore> effectiveRef(@Nonnull NPCEntity npc,
                                                 @Nonnull Ref<EntityStore> fallback) {
        try {
            Ref<EntityStore> entityRef = npc.getReference();
            if (entityRef != null && entityRef.isValid()) {
                return entityRef;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static void markRemoving(@Nonnull NPCEntity npc) {
        npc.setToDespawn();
        npc.setDespawning(true);
        npc.setDespawnTime(0f);
        npc.setDespawnRemainingSeconds(0f);
        npc.setDespawnCheckRemainingSeconds(0f);
    }

    private static void registerRef(@Nonnull String shopName,
                                    @Nonnull String worldName,
                                    @Nonnull Ref<EntityStore> ref) {
        NPC_REF_CACHE.put(cacheKey(shopName, worldName), ref);
    }

    private static void unregisterRef(@Nonnull String shopName,
                                      @Nonnull String worldName) {
        NPC_REF_CACHE.remove(cacheKey(shopName, worldName));
    }

    private static void unregisterRefByRef(@Nonnull Ref<EntityStore> ref) {
        NPC_REF_CACHE.entrySet().removeIf(entry -> ref.equals(entry.getValue()));
    }

    @Nonnull
    private static String cacheKey(@Nonnull String shopName, @Nonnull String worldName) {
        return shopName.toLowerCase(Locale.ROOT) + "|" + worldName.toLowerCase(Locale.ROOT);
    }

    private record NpcCandidate(@Nonnull Ref<EntityStore> ref,
                                boolean idMatch,
                                boolean nameMatch,
                                double distanceSquared) {
    }

    public record RoleSelection(@Nonnull String roleName, int roleIndex) {
    }

    public static final class NpcLifecycleResult {
        private final boolean success;
        private final boolean spawned;
        private final boolean moved;
        private final boolean busy;
        private final String npcId;
        private final int removedDuplicates;
        private final String reason;

        private NpcLifecycleResult(boolean success,
                                   boolean spawned,
                                   boolean moved,
                                   boolean busy,
                                   @Nonnull String npcId,
                                   int removedDuplicates,
                                   @Nonnull String reason) {
            this.success = success;
            this.spawned = spawned;
            this.moved = moved;
            this.busy = busy;
            this.npcId = npcId;
            this.removedDuplicates = removedDuplicates;
            this.reason = reason;
        }

        @Nonnull
        private static NpcLifecycleResult spawned(@Nonnull String npcId, int removedDuplicates) {
            return new NpcLifecycleResult(true, true, false, false, npcId, removedDuplicates, "");
        }

        @Nonnull
        private static NpcLifecycleResult moved(@Nonnull String npcId, int removedDuplicates) {
            return new NpcLifecycleResult(true, false, true, false, npcId, removedDuplicates, "");
        }

        @Nonnull
        private static NpcLifecycleResult busyResult() {
            return new NpcLifecycleResult(false, false, false, true, "", 0, "busy");
        }

        @Nonnull
        private static NpcLifecycleResult failed(@Nonnull String reason) {
            return new NpcLifecycleResult(false, false, false, false, "", 0, reason);
        }

        public boolean success() {
            return success;
        }

        public boolean spawned() {
            return spawned;
        }

        public boolean moved() {
            return moved;
        }

        public boolean busy() {
            return busy;
        }

        @Nonnull
        public String npcId() {
            return npcId;
        }

        public int removedDuplicates() {
            return removedDuplicates;
        }

        @Nonnull
        public String reason() {
            return reason;
        }
    }
}
