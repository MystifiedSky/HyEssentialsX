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
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerCompatUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopPlacementUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcEntityUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcNameplateUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerShopNpcCommand extends AbstractAsyncCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.playershop.npc";
    private static final String ADMIN_PERMISSION = "hyessentialsx.playershop.admin";
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
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addSubCommand(new SpawnSubCommand());
        this.addSubCommand(new RemoveSubCommand());
        this.addSubCommand(new ListSubCommand());
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if (!config.isPlayerShopsEnabled()) {
            Messages.sendKey(ctx, "shop.player.disabled", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }
        Messages.sendKey(ctx, "shop.player.npc.usage", java.util.Map.of());
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> executeNpcAction(@Nonnull CommandContext ctx, @Nonnull String action, @Nonnull String shopName) {
        if (!config.isPlayerShopsEnabled()) {
            Messages.sendKey(ctx, "shop.player.disabled", java.util.Map.of());
            return CompletableFuture.completedFuture(null);
        }
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
            super("spawn", "Spawn a player shop NPC");
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
            super("remove", "Remove a nearby player shop NPC");
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
            super("list", "List player shop NPC IDs");
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
        if (!ignorePermission) {
            CommandSender sender = ctx.sender();
            boolean canNpc = xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(sender, PERMISSION_NODE) || xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(sender, ADMIN_PERMISSION);
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
        if ("spawn".equals(action) || "create".equals(action)) {
            if (args.size() < 2) {
                Messages.sendKey(ctx, "shop.player.npc.usage", java.util.Map.of());
                return;
            }
            String shopName = args.get(1);
            world.execute(() -> spawnNpc(ctx, playerRef, world, shopName));
            return;
        }

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
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (ShopNpcEntityUtil.hasExistingShopNpc(world, store, shop)) {
            Messages.sendPrefixedKey(playerRef, "shop.npc.already_exists", java.util.Map.of());
            return;
        }
        if (!hasAccess(ctx, playerRef, shop)) {
            Messages.noPerm(ctx, "/shopnpc " + shopName);
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
        if (!ShopPlacementUtil.canPlaceShop(playerRef, world, store, playerEntityRef, playerPos)) {
            Messages.sendPrefixedKey(playerRef, "shop.player.claim_blocked", java.util.Map.of());
            return;
        }
        if (!chargeNpcCost(ctx, playerRef)) {
            return;
        }
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
        if (xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(ctx.sender(), ADMIN_PERMISSION)) {
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

}

