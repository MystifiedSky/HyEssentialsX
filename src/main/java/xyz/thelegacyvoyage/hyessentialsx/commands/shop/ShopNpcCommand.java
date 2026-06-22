package xyz.thelegacyvoyage.hyessentialsx.commands.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
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
            ctx.sendMessage(Message.raw("Players only."));
            return CompletableFuture.completedFuture(null);
        }
        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) {
            ctx.sendMessage(Message.raw("Could not get player reference."));
            return CompletableFuture.completedFuture(null);
        }
        World world = player.getWorld();
        if (world == null) {
            ctx.sendMessage(Message.raw("Could not get world."));
            return CompletableFuture.completedFuture(null);
        }

        List<String> args = CommandInputUtil.getArgs(ctx);
        if (args.isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /adminshopnpc <shop> | /adminshopnpc remove <shop>"));
            return CompletableFuture.completedFuture(null);
        }

        String action = args.get(0).toLowerCase();
        if ("remove".equals(action) || "delete".equals(action)) {
            if (args.size() < 2) {
                ctx.sendMessage(Message.raw("Usage: /adminshopnpc remove <shop>"));
                return CompletableFuture.completedFuture(null);
            }
            String shopName = args.get(1);
            world.execute(() -> removeNpc(ctx, playerRef, world, shopName));
            return CompletableFuture.completedFuture(null);
        }

        if ("list".equals(action)) {
            if (args.size() < 2) {
                ctx.sendMessage(Message.raw("Usage: /adminshopnpc list <shop>"));
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
            ctx.sendMessage(Message.raw("Shop not found."));
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> playerEntityRef = world.getEntityStore().getRefFromUUID(playerRef.getUuid());
        if (playerEntityRef == null) {
            ctx.sendMessage(Message.raw("Could not find player entity."));
            return;
        }
        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            ctx.sendMessage(Message.raw("Could not get player position."));
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
            ctx.sendMessage(Message.raw("No valid NPC roles found."));
            return;
        }

        Pair<Ref<EntityStore>, NPCEntity> npcPair =
                npcPlugin.spawnEntity(store, roleIndex, spawnPos, rotation, null, null);
        if (npcPair == null) {
            ctx.sendMessage(Message.raw("Failed to spawn shop NPC."));
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

        Messages.sendPrefixed(playerRef, "&aShop NPC spawned for &f" + shop.getDisplayName() + "&a.");
    }

    private void removeNpc(@Nonnull CommandContext ctx,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world,
                           @Nonnull String shopName) {
        ShopModel shop = shopManager.getShop(shopName);
        if (shop == null) {
            ctx.sendMessage(Message.raw("Shop not found."));
            return;
        }
        if (shop.getNpcs().isEmpty()) {
            Messages.sendPrefixed(playerRef, "&cNo NPCs registered for this shop.");
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> playerEntityRef = world.getEntityStore().getRefFromUUID(playerRef.getUuid());
        if (playerEntityRef == null) {
            ctx.sendMessage(Message.raw("Could not find player entity."));
            return;
        }
        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            ctx.sendMessage(Message.raw("Could not get player position."));
            return;
        }
        Vector3d playerPos = transform.getPosition();

        ShopNpcModel nearest = shop.getNpcs().stream()
                .min(Comparator.comparingDouble(npc -> distance(playerPos, npc.getPosition())))
                .orElse(null);
        if (nearest == null) {
            Messages.sendPrefixed(playerRef, "&cNo NPCs found.");
            return;
        }

        if (distance(playerPos, nearest.getPosition()) > 5.0D) {
            Messages.sendPrefixed(playerRef, "&cNo shop NPC nearby (within 5 blocks).");
            return;
        }

        despawnNpc(world, store, nearest.getNpcId());
        shop.getNpcs().removeIf(npc -> nearest.getNpcId().equalsIgnoreCase(npc.getNpcId()));
        shopManager.saveShop(shop);
        Messages.sendPrefixed(playerRef, "&cShop NPC removed.");
    }

    private void listNpcs(@Nonnull CommandContext ctx, @Nonnull String shopName) {
        ShopModel shop = shopManager.getShop(shopName);
        if (shop == null) {
            ctx.sendMessage(Message.raw("Shop not found."));
            return;
        }
        if (shop.getNpcs().isEmpty()) {
            ctx.sendMessage(Message.raw("No NPCs registered for this shop."));
            return;
        }
        String ids = String.join(", ", shop.getNpcs().stream().map(ShopNpcModel::getNpcId).toList());
        ctx.sendMessage(Message.raw("NPCs: " + ids));
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
        Nameplate nameplate = store.getComponent(npcRef, Nameplate.getComponentType());
        if (nameplate == null) {
            store.addComponent(npcRef, Nameplate.getComponentType(), new Nameplate(displayName));
        } else {
            nameplate.setText(displayName);
        }
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
