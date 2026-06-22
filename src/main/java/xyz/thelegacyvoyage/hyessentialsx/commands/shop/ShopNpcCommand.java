package xyz.thelegacyvoyage.hyessentialsx.commands.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcNameplateUtil;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ShopNpcCommand extends AbstractAsyncCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.shop.npc";
    private static final String[] PREFERRED_ROLES = {
            "Klops_Merchant",
            "Feran_Civilian",
            "Kweebec_Civilian",
            "Trork_Civilian",
            "Human_Civilian"
    };

    private final ShopManager shopManager;

    public ShopNpcCommand(@Nonnull ShopManager shopManager) {
        super("adminshopnpc", "Spawn or remove admin shop NPCs");
        this.shopManager = shopManager;
        this.requirePermission(PERMISSION_NODE);
        this.setAllowsExtraArguments(true);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
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
        if (args.isEmpty()) {
            Messages.sendKey(ctx, "shop.npc.usage", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }

        String action = args.get(0).toLowerCase();
        if ("remove".equals(action) || "delete".equals(action)) {
            if (args.size() < 2) {
                Messages.sendKey(ctx, "shop.npc.usage.remove", java.util.Map.of());
                return CompletableFuture.completedFuture(null);
            }
            String shopName = args.get(1);
            world.execute(() -> removeNpc(ctx, playerRef, world, shopName));
            return CompletableFuture.completedFuture(null);
        }

        if ("list".equals(action)) {
            if (args.size() < 2) {
                Messages.sendKey(ctx, "shop.npc.usage.list", java.util.Map.of());
                return CompletableFuture.completedFuture(null);
            }
            String shopName = args.get(1);
            listNpcs(ctx, shopName);
            return CompletableFuture.completedFuture(null);
        }

        String shopName = args.get(0);
        world.execute(() -> spawnNpc(ctx, playerRef, world, shopName));
        return CompletableFuture.completedFuture(null);
    }

    private void spawnNpc(@Nonnull CommandContext ctx,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull World world,
                          @Nonnull String shopName) {
        ShopModel shop = shopManager.getShop(shopName);
        if (shop == null) {
            Messages.sendKey(ctx, "shop.npc.shop_not_found", java.util.Map.of());
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
        if (shop == null) {
            Messages.sendKey(ctx, "shop.npc.shop_not_found", java.util.Map.of());
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

        ShopNpcModel nearest = shop.getNpcs().stream()
                .min(Comparator.comparingDouble(npc -> distance(playerPos, npc.getPosition())))
                .orElse(null);
        if (nearest == null) {
            Messages.sendPrefixedKey(playerRef, "shop.npc.none_found", java.util.Map.of());
            return;
        }

        if (distance(playerPos, nearest.getPosition()) > 5.0D) {
            Messages.sendPrefixedKey(playerRef, "shop.npc.none_nearby", java.util.Map.of());
            return;
        }

        despawnNpc(world, store, nearest.getNpcId());
        shop.getNpcs().removeIf(npc -> nearest.getNpcId().equalsIgnoreCase(npc.getNpcId()));
        shopManager.saveShop(shop);
        Messages.sendPrefixedKey(playerRef, "shop.npc.removed", java.util.Map.of());
    }

    private void listNpcs(@Nonnull CommandContext ctx, @Nonnull String shopName) {
        ShopModel shop = shopManager.getShop(shopName);
        if (shop == null) {
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

    private void despawnNpc(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull String npcId) {
        try {
            UUID npcUuid = UUID.fromString(npcId);
            store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
                try {
                    Ref<EntityStore> ref = chunk.getReferenceTo(index);
                    NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                    if (npc != null && npc.getUuid().equals(npcUuid)) {
                        npc.setDespawning(true);
                        npc.setDespawnTime(0f);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private double distance(@Nonnull Vector3d playerPos, @Nonnull Vector3i blockPos) {
        double dx = playerPos.getX() - (blockPos.getX() + 0.5D);
        double dy = playerPos.getY() - blockPos.getY();
        double dz = playerPos.getZ() - (blockPos.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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
