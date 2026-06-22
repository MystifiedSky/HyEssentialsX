package xyz.thelegacyvoyage.hyessentialsx.commands.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopPlacementUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcNameplateUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerShopNpcCommand extends AbstractAsyncCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.playershop.npc";
    private static final String ADMIN_PERMISSION = "hyessentialsx.playershop.admin";
    private static final String[] PREFERRED_ROLES = {
            "Klops_Merchant",
            "Feran_Civilian",
            "Kweebec_Civilian",
            "Trork_Civilian",
            "Human_Civilian"
    };

    private final ShopManager shopManager;
    private final EconomyManager economy;
    private final ConfigManager config;

    public PlayerShopNpcCommand(@Nonnull ShopManager shopManager,
                                @Nonnull EconomyManager economy,
                                @Nonnull ConfigManager config) {
        super("shopnpc", "Spawn or remove player shop NPCs");
        this.shopManager = shopManager;
        this.economy = economy;
        this.config = config;
        this.requirePermission(PERMISSION_NODE);
        this.setAllowsExtraArguments(true);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if (!config.isPlayerShopsEnabled()) {
            Messages.sendKey(ctx, "shop.player.disabled", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            Messages.sendKey(ctx, "shop.npc.players_only", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }
        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) {
            Messages.sendKey(ctx, "shop.npc.player_ref_failed", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }
        World world = player.getWorld();
        if (world == null) {
            Messages.sendKey(ctx, "shop.npc.world_failed", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }

        List<String> args = CommandInputUtil.getArgs(ctx);
        handleNpcCommand(ctx, playerRef, world, args, false);
        return CompletableFuture.completedFuture(null);
    }

    public void handleNpcCommand(@Nonnull CommandContext ctx,
                                 @Nonnull PlayerRef playerRef,
                                 @Nonnull World world,
                                 @Nonnull List<String> args,
                                 boolean ignorePermission) {
        if (!ignorePermission) {
            CommandSender sender = ctx.sender();
            boolean canNpc = sender.hasPermission(PERMISSION_NODE) || sender.hasPermission(ADMIN_PERMISSION);
            if (!canNpc) {
                Messages.noPerm(ctx, "/shopnpc");
                return;
            }
        }

        if (args.isEmpty()) {
            Messages.sendKey(ctx, "shop.player.npc.usage", java.util.Map.of());
            return;
        }

        String action = args.get(0).toLowerCase();
        if ("remove".equals(action) || "delete".equals(action)) {
            if (args.size() < 2) {
                Messages.sendKey(ctx, "shop.player.npc.usage.remove", java.util.Map.of());
                return;
            }
            String shopName = args.get(1);
            world.execute(() -> removeNpc(ctx, playerRef, world, shopName));
            return;
        }

        if ("list".equals(action)) {
            if (args.size() < 2) {
                Messages.sendKey(ctx, "shop.player.npc.usage.list", java.util.Map.of());
                return;
            }
            String shopName = args.get(1);
            listNpcs(ctx, shopName);
            return;
        }

        String shopName = args.get(0);
        world.execute(() -> spawnNpc(ctx, playerRef, world, shopName));
    }

    public void spawnNpcDirect(@Nonnull CommandContext ctx,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world,
                               @Nonnull String shopName) {
        world.execute(() -> spawnNpc(ctx, playerRef, world, shopName));
    }

    private void spawnNpc(@Nonnull CommandContext ctx,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull World world,
                          @Nonnull String shopName) {
        ShopModel shop = shopManager.getShop(shopName);
        if (shop == null || !shop.isPlayerShop()) {
            Messages.sendKey(ctx, "shop.npc.shop_not_found", java.util.Map.of());
            return;
        }
        if (!shop.getNpcs().isEmpty()) {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (cleanupStaleNpcs(world, store, shop)) {
                Messages.sendPrefixedKey(playerRef, "shop.npc.already_exists", java.util.Map.of());
                return;
            }
        }
        if (!hasAccess(ctx, playerRef, shop)) {
            Messages.noPerm(ctx, "/shopnpc " + shopName);
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> playerEntityRef = world.getEntityStore().getRefFromUUID(playerRef.getUuid());
        if (playerEntityRef == null) {
            Messages.sendKey(ctx, "shop.npc.player_entity_missing", java.util.Map.of());
            return;
        }
        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            Messages.sendKey(ctx, "shop.npc.player_pos_failed", java.util.Map.of());
            return;
        }

        Vector3d playerPos = transform.getPosition();
        if (!ShopPlacementUtil.canPlaceShop(playerRef, world, store, playerEntityRef, playerPos)) {
            Messages.sendPrefixedKey(playerRef, "shop.player.claim_blocked", java.util.Map.of());
            return;
        }
        if (!chargeNpcCost(ctx, playerRef)) {
            return;
        }
        Vector3i basePos = new Vector3i(
                (int) Math.floor(playerPos.getX()),
                (int) Math.floor(playerPos.getY()),
                (int) Math.floor(playerPos.getZ())
        );
        Vector3d spawnPos = new Vector3d(basePos.getX() + 0.5D, basePos.getY(), basePos.getZ() + 0.5D);
        Vector3f rotation = transform.getRotation() != null ? transform.getRotation() : new Vector3f(0f, 0f, 0f);

        NPCPlugin npcPlugin = NPCPlugin.get();
        List<String> availableRoles = npcPlugin.getRoleTemplateNames(true);
        String selectedRole = null;
        int roleIndex = -1;

        String preferredRole = shop.getNpcRole();
        if (!preferredRole.isBlank() && availableRoles.contains(preferredRole)) {
            int idx = npcPlugin.getIndex(preferredRole);
            if (idx >= 0) {
                selectedRole = preferredRole;
                roleIndex = idx;
            }
        }

        for (String preferred : PREFERRED_ROLES) {
            if (selectedRole != null) break;
            if (availableRoles.contains(preferred)) {
                roleIndex = npcPlugin.getIndex(preferred);
                if (roleIndex >= 0) {
                    selectedRole = preferred;
                    break;
                }
            }
        }

        if (selectedRole == null && !availableRoles.isEmpty()) {
            for (String role : availableRoles) {
                roleIndex = npcPlugin.getIndex(role);
                if (roleIndex >= 0) {
                    selectedRole = role;
                    break;
                }
            }
        }

        if (selectedRole == null || roleIndex < 0) {
            Messages.sendKey(ctx, "shop.npc.no_roles", java.util.Map.of());
            return;
        }

        Pair<Ref<EntityStore>, NPCEntity> npcPair =
                npcPlugin.spawnEntity(store, roleIndex, spawnPos, rotation, null, null);
        if (npcPair == null) {
            Messages.sendKey(ctx, "shop.npc.spawn_failed", java.util.Map.of());
            return;
        }

        Ref<EntityStore> npcRef = npcPair.first();
        NPCEntity spawnedNpc = npcPair.second();
        applyNpcDefaults(store, npcRef, spawnedNpc, basePos, rotation, shop.getDisplayName());

        String npcId = spawnedNpc.getUuid().toString();
        ShopNpcModel loc = new ShopNpcModel(
                npcId,
                basePos,
                world.getName(),
                shop.getName(),
                playerRef.getUuid().toString(),
                playerRef.getUsername() == null ? "" : playerRef.getUsername(),
                selectedRole
        );
        shop.getNpcs().removeIf(npc -> npcId.equalsIgnoreCase(npc.getNpcId()));
        shop.getNpcs().add(loc);
        shopManager.saveShop(shop);

        Messages.sendPrefixedKey(playerRef, "shop.npc.spawned", java.util.Map.of("shop", shop.getDisplayName()));
    }

    private void removeNpc(@Nonnull CommandContext ctx,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world,
                           @Nonnull String shopName) {
        ShopModel shop = shopManager.getShop(shopName);
        if (shop == null || !shop.isPlayerShop()) {
            Messages.sendKey(ctx, "shop.npc.shop_not_found", java.util.Map.of());
            return;
        }
        if (!hasAccess(ctx, playerRef, shop)) {
            Messages.noPerm(ctx, "/shopnpc remove " + shopName);
            return;
        }
        if (shop.getNpcs().isEmpty()) {
            Messages.sendPrefixedKey(playerRef, "shop.npc.none_registered", java.util.Map.of());
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> playerEntityRef = world.getEntityStore().getRefFromUUID(playerRef.getUuid());
        if (playerEntityRef == null) {
            Messages.sendKey(ctx, "shop.npc.player_entity_missing", java.util.Map.of());
            return;
        }
        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            Messages.sendKey(ctx, "shop.npc.player_pos_failed", java.util.Map.of());
            return;
        }
        Vector3d playerPos = transform.getPosition();

        boolean anyRemoved = false;
        for (ShopNpcModel npcModel : List.copyOf(shop.getNpcs())) {
            if (distance(playerPos, npcModel.getPosition()) > 5.0D) {
                continue;
            }
            boolean removed = despawnNpc(world, store, npcModel.getNpcId());
            if (!removed) {
                removed = despawnNpcByPosition(world, store, npcModel.getPosition());
            }
            anyRemoved |= removed;
            shop.getNpcs().removeIf(npc -> npcModel.getNpcId().equalsIgnoreCase(npc.getNpcId()));
        }
        if (!anyRemoved) {
            ShopNpcModel nearest = findNearestNpc(shop, world.getName(), playerPos);
            if (nearest != null) {
                boolean removed = despawnNpc(world, store, nearest.getNpcId());
                if (!removed) {
                    removed = despawnNpcByPosition(world, store, nearest.getPosition());
                }
                if (removed) {
                    shop.getNpcs().removeIf(npc -> nearest.getNpcId().equalsIgnoreCase(npc.getNpcId()));
                    anyRemoved = true;
                }
            }
        }
        if (!anyRemoved) {
            Messages.sendPrefixedKey(playerRef, "shop.npc.none_nearby", java.util.Map.of());
            return;
        }
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, "shop.npc.removed", java.util.Map.of());
    }

    private void listNpcs(@Nonnull CommandContext ctx, @Nonnull String shopName) {
        ShopModel shop = shopManager.getShop(shopName);
        if (shop == null || !shop.isPlayerShop()) {
            Messages.sendKey(ctx, "shop.npc.shop_not_found", java.util.Map.of());
            return;
        }
        if (shop.getNpcs().isEmpty()) {
            Messages.sendKey(ctx, "shop.npc.none_registered", java.util.Map.of());
            return;
        }
        String ids = String.join(", ", shop.getNpcs().stream().map(ShopNpcModel::getNpcId).toList());
        Messages.sendKey(ctx, "shop.npc.list", java.util.Map.of("ids", ids));
    }

    // NPC count limits are enforced at the shop level (one NPC per shop).

    private boolean chargeNpcCost(@Nonnull CommandContext ctx, @Nonnull PlayerRef playerRef) {
        long cost = Math.max(0L, config.getPlayerShopCreationCost());
        if (cost <= 0L) {
            return true;
        }
        if (!economy.isEnabled()) {
            Messages.sendPrefixedKey(playerRef, "shop.player.npc.economy_disabled", java.util.Map.of());
            return false;
        }
        if (economy.getBalance(playerRef.getUuid()) < cost) {
            Messages.sendPrefixedKey(playerRef, "shop.player.npc.cost_insufficient",
                    java.util.Map.of("amount", economy.formatAmount(cost)));
            return false;
        }
        if (!economy.withdraw(playerRef.getUuid(), cost)) {
            Messages.sendPrefixedKey(playerRef, "shop.player.npc.cost_failed", java.util.Map.of());
            return false;
        }
        return true;
    }


    private boolean hasAccess(@Nonnull CommandContext ctx, @Nonnull PlayerRef playerRef, @Nonnull ShopModel shop) {
        if (ctx.sender().hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        String uuid = playerRef.getUuid().toString();
        if (uuid.equalsIgnoreCase(shop.getOwnerUuid())) {
            return true;
        }
        for (String editor : shop.getEditors()) {
            if (editor == null) continue;
            if (editor.equalsIgnoreCase(uuid)) {
                return true;
            }
            if (playerRef.getUsername() != null && editor.equalsIgnoreCase(playerRef.getUsername())) {
                return true;
            }
        }
        return false;
    }

    private boolean despawnNpc(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull String npcId) {
        try {
            UUID npcUuid = UUID.fromString(npcId);
            final boolean[] removed = {false};
            store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
                try {
                    Ref<EntityStore> ref = chunk.getReferenceTo(index);
                    NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                    if (npc != null && npc.getUuid().equals(npcUuid)) {
                        npc.setToDespawn();
                        npc.setDespawning(true);
                        npc.setDespawnTime(0f);
                        npc.setDespawnRemainingSeconds(0f);
                        npc.setDespawnCheckRemainingSeconds(0f);
                        commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
                        removed[0] = true;
                    }
                } catch (Exception ignored) {
                }
            });
            return removed[0];
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean despawnNpcByPosition(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Vector3i position) {
        final boolean[] removed = {false};
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null) return;
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null || transform.getPosition() == null) return;
                Vector3d pos = transform.getPosition();
                double dx = Math.abs(pos.getX() - (position.getX() + 0.5D));
                double dy = Math.abs(pos.getY() - position.getY());
                double dz = Math.abs(pos.getZ() - (position.getZ() + 0.5D));
                if (dx < 1.5D && dy < 2.0D && dz < 1.5D) {
                    Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
                    String interactionId = interactions != null
                            ? interactions.getInteractionId(InteractionType.Use)
                            : null;
                    if (interactionId == null
                            || !interactionId.equalsIgnoreCase(ShopNpcInteractionRegistry.ADMIN_SHOP_ROOT_INTERACTION_ID)) {
                        return;
                    }
                    npc.setToDespawn();
                    npc.setDespawning(true);
                    npc.setDespawnTime(0f);
                    npc.setDespawnRemainingSeconds(0f);
                    npc.setDespawnCheckRemainingSeconds(0f);
                    commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
                    removed[0] = true;
                }
            } catch (Exception ignored) {
            }
        });
        return removed[0];
    }

    private double distance(@Nonnull Vector3d playerPos, @Nonnull Vector3i blockPos) {
        double dx = playerPos.getX() - (blockPos.getX() + 0.5D);
        double dy = playerPos.getY() - blockPos.getY();
        double dz = playerPos.getZ() - (blockPos.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean cleanupStaleNpcs(@Nonnull World world,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull ShopModel shop) {
        String worldName = world.getName();
        boolean foundInOtherWorld = false;
        java.util.Set<java.util.UUID> knownIds = new java.util.HashSet<>();
        java.util.List<Vector3i> positions = new java.util.ArrayList<>();
        java.util.List<ShopNpcModel> currentWorld = new java.util.ArrayList<>();
        for (ShopNpcModel npc : shop.getNpcs()) {
            if (npc == null) continue;
            if (!npc.getWorldId().equalsIgnoreCase(worldName)) {
                foundInOtherWorld = true;
                continue;
            }
            currentWorld.add(npc);
            positions.add(npc.getPosition());
            String id = npc.getNpcId();
            if (id == null || id.isBlank()) continue;
            try {
                knownIds.add(java.util.UUID.fromString(id));
            } catch (Exception ignored) {
            }
        }
        if (currentWorld.isEmpty()) {
            return foundInOtherWorld;
        }
        java.util.concurrent.atomic.AtomicBoolean found = new java.util.concurrent.atomic.AtomicBoolean(false);
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            if (found.get()) return;
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null) return;
                Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
                String interactionId = interactions != null
                        ? interactions.getInteractionId(InteractionType.Use)
                        : null;
                if (interactionId == null
                        || !interactionId.equalsIgnoreCase(ShopNpcInteractionRegistry.ADMIN_SHOP_ROOT_INTERACTION_ID)) {
                    return;
                }
                try {
                    if (knownIds.contains(npc.getUuid())) {
                        found.set(true);
                        return;
                    }
                } catch (Exception ignored) {
                }
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null || transform.getPosition() == null) return;
                Vector3d pos = transform.getPosition();
                for (Vector3i stored : positions) {
                    double dx = Math.abs(pos.getX() - (stored.getX() + 0.5D));
                    double dy = Math.abs(pos.getY() - stored.getY());
                    double dz = Math.abs(pos.getZ() - (stored.getZ() + 0.5D));
                    if (dx < 1.5D && dy < 2.0D && dz < 1.5D) {
                        found.set(true);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        });
        if (!found.get()) {
            shop.getNpcs().removeIf(npc -> npc != null && npc.getWorldId().equalsIgnoreCase(worldName));
            shopManager.saveShop(shop);
        }
        return found.get() || foundInOtherWorld;
    }

    private ShopNpcModel findNearestNpc(@Nonnull ShopModel shop,
                                        @Nonnull String worldName,
                                        @Nonnull Vector3d playerPos) {
        ShopNpcModel best = null;
        double bestDist = Double.MAX_VALUE;
        for (ShopNpcModel npcModel : shop.getNpcs()) {
            if (npcModel == null) continue;
            if (!npcModel.getWorldId().equalsIgnoreCase(worldName)) continue;
            double dist = distance(playerPos, npcModel.getPosition());
            if (dist < bestDist) {
                bestDist = dist;
                best = npcModel;
            }
        }
        return best;
    }

    private void applyNpcDefaults(@Nonnull Store<EntityStore> store,
                                  @Nonnull Ref<EntityStore> npcRef,
                                  @Nonnull NPCEntity npc,
                                  @Nonnull Vector3i blockPos,
                                  @Nonnull Vector3f rotation,
                                  @Nonnull String displayName) {
        if (store.getComponent(npcRef, Interactable.getComponentType()) == null) {
            store.addComponent(npcRef, Interactable.getComponentType(), Interactable.INSTANCE);
        }
        store.addComponent(npcRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        store.addComponent(npcRef, Frozen.getComponentType(), Frozen.get());
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
        npc.setLeashPoint(new Vector3d(blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D));
        npc.setLeashHeading(rotation.getY());
        ShopNpcInteractionRegistry.applyNpcInteractions(store, npcRef);
    }
}

