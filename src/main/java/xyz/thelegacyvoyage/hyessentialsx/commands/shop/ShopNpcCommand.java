package xyz.thelegacyvoyage.hyessentialsx.commands.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
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
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerCompatUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcEntityUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcNameplateUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ShopNpcCommand extends AbstractAsyncCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.adminshop.npc";
    private static final String LEGACY_PERMISSION_NODE = "hyessentialsx.shop.npc";
    private final ShopManager shopManager;

    public ShopNpcCommand(@Nonnull ShopManager shopManager) {
        super("adminshopnpc", "Spawn or remove admin shop NPCs");
        this.shopManager = shopManager;
        this.addSubCommand(new SpawnSubCommand());
        this.addSubCommand(new RemoveSubCommand());
        this.addSubCommand(new ListSubCommand());
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        Messages.sendKey(ctx, "shop.npc.usage", java.util.Map.of());
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> executeNpcAction(@Nonnull CommandContext ctx, @Nonnull String action, @Nonnull String shopName) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            Messages.sendKey(ctx, "shop.npc.players_only", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }
        PlayerRef playerRef = ServerCompatUtil.getPlayerRef(player);
        if (playerRef == null) {
            Messages.sendKey(ctx, "shop.npc.player_ref_failed", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }
        World world = player.getWorld();
        if (world == null) {
            Messages.sendKey(ctx, "shop.npc.world_failed", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }

        handleNpcCommand(ctx, playerRef, world, List.of(action, shopName), false);
        return CompletableFuture.completedFuture(null);
    }

    private final class SpawnSubCommand extends AbstractAsyncCommand {
        private final RequiredArg<String> shopArg;

        private SpawnSubCommand() {
            super("spawn", "Spawn an admin shop NPC");
            this.shopArg = withRequiredArg("shop", "Shop name", ArgTypes.STRING);
            this.addAliases(new String[]{"create"});
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            return executeNpcAction(ctx, "spawn", ctx.get(shopArg));
        }
    }

    private final class RemoveSubCommand extends AbstractAsyncCommand {
        private final RequiredArg<String> shopArg;

        private RemoveSubCommand() {
            super("remove", "Remove a nearby admin shop NPC");
            this.shopArg = withRequiredArg("shop", "Shop name", ArgTypes.STRING);
            this.addAliases(new String[]{"delete"});
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            return executeNpcAction(ctx, "remove", ctx.get(shopArg));
        }
    }

    private final class ListSubCommand extends AbstractAsyncCommand {
        private final RequiredArg<String> shopArg;

        private ListSubCommand() {
            super("list", "List admin shop NPC IDs");
            this.shopArg = withRequiredArg("shop", "Shop name", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            return executeNpcAction(ctx, "list", ctx.get(shopArg));
        }
    }

    public void handleNpcCommand(@Nonnull CommandContext ctx,
                                 @Nonnull PlayerRef playerRef,
                                 @Nonnull World world,
                                 @Nonnull List<String> args,
                                 boolean ignorePermission) {
        CommandSender sender = ctx.sender();
        if (!ignorePermission
                && !hasPermission(sender, playerRef, PERMISSION_NODE)
                && !hasPermission(sender, playerRef, LEGACY_PERMISSION_NODE)) {
            Messages.noPerm(ctx, "/adminshopnpc");
            return;
        }

        if (args.isEmpty()) {
            Messages.sendKey(ctx, "shop.npc.usage", java.util.Map.of());
            return;
        }

        String action = args.get(0).toLowerCase();
        if ("spawn".equals(action) || "create".equals(action)) {
            if (args.size() < 2) {
                Messages.sendKey(ctx, "shop.npc.usage", java.util.Map.of());
                return;
            }
            String shopName = args.get(1);
            world.execute(() -> spawnNpc(ctx, playerRef, world, shopName));
            return;
        }

        if ("remove".equals(action) || "delete".equals(action)) {
            if (args.size() < 2) {
                Messages.sendKey(ctx, "shop.npc.usage.remove", java.util.Map.of());
                return;
            }
            String shopName = args.get(1);
            world.execute(() -> removeNpc(ctx, playerRef, world, shopName));
            return;
        }

        if ("list".equals(action)) {
            if (args.size() < 2) {
                Messages.sendKey(ctx, "shop.npc.usage.list", java.util.Map.of());
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
        if (shop == null || shop.isPlayerShop()) {
            Messages.sendKey(ctx, "shop.npc.shop_not_found", java.util.Map.of());
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (ShopNpcEntityUtil.hasExistingShopNpc(world, store, shop)) {
            Messages.sendPrefixedKey(playerRef, "shop.npc.already_exists", java.util.Map.of());
            return;
        }
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
                (int) Math.floor(playerPos.x()),
                (int) Math.floor(playerPos.y()),
                (int) Math.floor(playerPos.z())
        );
        Vector3d spawnPos = new Vector3d(basePos.x() + 0.5D, basePos.y(), basePos.z() + 0.5D);
        com.hypixel.hytale.math.vector.Rotation3f rotation = transform.getRotation() != null ? transform.getRotation() : new com.hypixel.hytale.math.vector.Rotation3f(0f, 0f, 0f);

        ShopNpcEntityUtil.RoleSelection role = ShopNpcEntityUtil.resolveRoleSelection(shop.getNpcRole());
        if (role == null) {
            Messages.sendKey(ctx, "shop.npc.no_roles", java.util.Map.of());
            return;
        }

        ShopNpcEntityUtil.NpcLifecycleResult result = ShopNpcEntityUtil.moveOrSpawnShopNpc(
                world,
                store,
                shop,
                null,
                spawnPos,
                rotation,
                basePos,
                shop.getDisplayName(),
                playerRef.getUuid().toString(),
                playerRef.getUsername() == null ? "" : playerRef.getUsername(),
                role.roleName(),
                role.roleIndex()
        );
        if (!result.success()) {
            Messages.sendKey(ctx, "shop.npc.spawn_failed", java.util.Map.of());
            return;
        }
        shopManager.saveShop(shop);

        Messages.sendPrefixedKey(playerRef, "shop.npc.spawned", java.util.Map.of("shop", shop.getDisplayName()));
    }

    private void removeNpc(@Nonnull CommandContext ctx,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world,
                           @Nonnull String shopName) {
        ShopModel shop = shopManager.getShop(shopName);
        if (shop == null || shop.isPlayerShop()) {
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

        boolean anyRemoved = false;
        for (ShopNpcModel npcModel : List.copyOf(shop.getNpcs())) {
            if (distance(playerPos, npcModel.getPosition()) > 5.0D) {
                continue;
            }
            boolean removed = ShopNpcEntityUtil.removeAllMatchingShopNpcs(store, shop, npcModel) > 0;
            anyRemoved |= removed;
            shop.getNpcs().removeIf(npc -> npcModel.getNpcId().equalsIgnoreCase(npc.getNpcId()));
        }
        if (!anyRemoved) {
            ShopNpcModel nearest = findNearestNpc(shop, world.getName(), playerPos);
            if (nearest != null) {
                boolean removed = ShopNpcEntityUtil.removeAllMatchingShopNpcs(store, shop, nearest) > 0;
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
        if (shop == null || shop.isPlayerShop()) {
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

    private double distance(@Nonnull Vector3d playerPos, @Nonnull Vector3i blockPos) {
        double dx = playerPos.x() - (blockPos.x() + 0.5D);
        double dy = playerPos.y() - blockPos.y();
        double dz = playerPos.z() - (blockPos.z() + 0.5D);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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

    private boolean hasPermission(@Nonnull CommandSender sender,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull String permission) {
        boolean senderHas = xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(sender, permission);
        boolean playerHas = xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(playerRef, permission);
        return senderHas || playerHas;
    }
}

