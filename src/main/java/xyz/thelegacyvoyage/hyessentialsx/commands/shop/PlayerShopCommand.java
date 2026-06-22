package xyz.thelegacyvoyage.hyessentialsx.commands.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.protocol.InteractionType;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.PlayerShopBrowseUI;
import xyz.thelegacyvoyage.hyessentialsx.ui.ShopAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerCompatUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopContainerUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcEntityUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopPlacementUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcRemovalUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class PlayerShopCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.playershop.use";
    private static final String ADMIN_PERMISSION = "hyessentialsx.playershop.admin";
    private static final String LEGACY_PERMISSION = "hyessentialsx.playershop";
    private static final String CREATE_PERMISSION = "hyessentialsx.playershop.create";
    private static final String DELETE_PERMISSION = "hyessentialsx.playershop.delete";

    private final ShopManager shopManager;
    private final EconomyManager economy;
    private final ShopAdminDraftCache draftCache;
    private final ConfigManager config;
    private final StorageManager storage;

    public PlayerShopCommand(@Nonnull ShopManager shopManager,
                             @Nonnull EconomyManager economy,
                             @Nonnull ShopAdminDraftCache draftCache,
                             @Nonnull ConfigManager config,
                             @Nonnull StorageManager storage) {
        super("shop", "Open or manage player shops");
        this.shopManager = shopManager;
        this.economy = economy;
        this.draftCache = draftCache;
        this.config = config;
        this.storage = storage;
        this.setPermissionGroups();
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new OpenSubCommand());
        this.addSubCommand(new CreateSubCommand());
        this.addSubCommand(new DeleteSubCommand());
        this.addSubCommand(new EditSubCommand());
        this.addSubCommand(new MoveSubCommand());
        this.addSubCommand(new LinkSubCommand());
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        if (!ensurePlayerShopsEnabled(context)) return;

        if (!hasUsePermission(context.sender(), playerRef)) {
            Messages.noPerm(context, "/shop");
            return;
        }
        listShops(context, playerRef);
    }

    private boolean ensurePlayerShopsEnabled(@Nonnull CommandContext context) {
        if (config.isPlayerShopsEnabled()) {
            return true;
        }
        Messages.sendKey(context, "shop.player.disabled", java.util.Map.of());
        return false;
    }

    private final class ListSubCommand extends AbstractPlayerCommand {
        private ListSubCommand() {
            super("list", "List player shops");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!ensurePlayerShopsEnabled(context)) return;
            if (!hasUsePermission(context.sender(), playerRef)) {
                Messages.noPerm(context, "/shop list");
                return;
            }
            listShops(context, playerRef);
        }
    }

    private final class OpenSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private OpenSubCommand() {
            super("open", "Open a player shop");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
            this.addAliases(new String[]{"shop"});
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!ensurePlayerShopsEnabled(context)) return;
            if (!hasUsePermission(context.sender(), playerRef)) {
                Messages.noPerm(context, "/shop open");
                return;
            }
            openBrowse(context, store, ref, playerRef, context.get(nameArg));
        }
    }

    private final class CreateSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private CreateSubCommand() {
            super("create", "Create a player shop");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!ensurePlayerShopsEnabled(context)) return;
            if (!hasNodePermission(context.sender(), playerRef, ADMIN_PERMISSION)
                    && !hasNodePermission(context.sender(), playerRef, CREATE_PERMISSION)) {
                Messages.noPerm(context, "/shop create");
                return;
            }
            if (!hasNodePermission(context.sender(), playerRef, ADMIN_PERMISSION)) {
                int limit = config.getPlayerShopMaxShopsPerPlayer();
                if (limit > 0 && countShopsForOwner(playerRef.getUuid().toString()) >= limit) {
                    Messages.sendKey(context, "shop.player.limit_reached",
                            java.util.Map.of("limit", String.valueOf(limit)));
                    return;
                }
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null || transform.getPosition() == null) {
                Messages.sendKey(context, "shop.npc.player_pos_failed", java.util.Map.of());
                return;
            }
            if (!ShopPlacementUtil.canPlaceShop(playerRef, world, store, ref, transform.getPosition())) {
                Messages.sendKey(context, "shop.player.claim_blocked", java.util.Map.of());
                return;
            }
            ShopModel created = shopManager.createPlayerShop(context.get(nameArg), playerRef.getUuid().toString());
            if (created == null) {
                Messages.sendKey(context, "shop.admin.exists_or_invalid", java.util.Map.of());
                return;
            }
            Messages.sendKey(context, "shop.player.created", java.util.Map.of("shop", created.getDisplayName()));
            spawnShopNpc(context, playerRef, world, created);
        }
    }

    private final class DeleteSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private DeleteSubCommand() {
            super("delete", "Delete a player shop");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
            this.addAliases(new String[]{"remove"});
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!ensurePlayerShopsEnabled(context)) return;
            String name = context.get(nameArg);
            ShopModel shop = shopManager.getShop(name);
            if (shop == null || !shop.isPlayerShop()) {
                removeOrphanedShopNpcs(name, name);
                Messages.sendKey(context, "shop.player.deleted", java.util.Map.of());
                return;
            }
            if (!hasNodePermission(context.sender(), playerRef, ADMIN_PERMISSION)
                    && !hasNodePermission(context.sender(), playerRef, DELETE_PERMISSION)
                    && !isOwner(playerRef, shop)) {
                Messages.noPerm(context, "/shop delete " + name);
                return;
            }
            removeShopNpcs(shop);
            ShopNpcRemovalUtil.removeNearbyNpc(world, store, ref, shop);
            if (shopManager.deleteShop(name)) {
                Messages.sendKey(context, "shop.player.deleted", java.util.Map.of());
            } else {
                Messages.sendKey(context, "shop.player.not_found", java.util.Map.of());
            }
        }
    }

    private final class EditSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private EditSubCommand() {
            super("edit", "Edit a player shop");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!ensurePlayerShopsEnabled(context)) return;
            openAdmin(context, store, ref, playerRef, context.get(nameArg));
        }
    }

    private final class MoveSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private MoveSubCommand() {
            super("move", "Move a player shop NPC");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!ensurePlayerShopsEnabled(context)) return;
            moveShopNpc(context, store, ref, playerRef, world, context.get(nameArg));
        }
    }

    private final class LinkSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private LinkSubCommand() {
            super("link", "Link a chest to a player shop");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!ensurePlayerShopsEnabled(context)) return;
            linkChestForShop(context, store, ref, playerRef, world, context.get(nameArg));
        }
    }

    private void listShops(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef) {
        boolean isAdmin = hasNodePermission(context.sender(), playerRef, ADMIN_PERMISSION);
        List<String> names = shopManager.listPlayerShops();
        List<String> display = new ArrayList<>();
        if (isAdmin) {
            for (String name : names) {
                ShopModel shop = shopManager.getShop(name);
                if (shop != null && !shop.getDisplayName().equalsIgnoreCase(name)) {
                    display.add(name + " (" + shop.getDisplayName() + ")");
                } else {
                    display.add(name);
                }
            }
            if (display.isEmpty()) {
                Messages.sendKey(context, "shop.player.none_configured", java.util.Map.of());
                return;
            }
        } else {
            String owner = playerRef.getUuid().toString();
            for (String name : names) {
                ShopModel shop = shopManager.getShop(name);
                if (shop == null || !shop.isPlayerShop()) continue;
                if (!owner.equalsIgnoreCase(shop.getOwnerUuid())) continue;
                if (!shop.getDisplayName().equalsIgnoreCase(name)) {
                    display.add(name + " (" + shop.getDisplayName() + ")");
                } else {
                    display.add(name);
                }
            }
            if (display.isEmpty()) {
                Messages.sendKey(context, "shop.player.none_owned", java.util.Map.of());
                return;
            }
        }
        Messages.sendKey(context, "shop.player.list", java.util.Map.of("shops", String.join(", ", display)));
    }

    private void openBrowse(@Nonnull CommandContext context,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull String name) {
        ShopModel shop = shopManager.getShop(name);
        if (shop == null || !shop.isPlayerShop()) {
            Messages.sendKey(context, "shop.player.not_found", java.util.Map.of());
            return;
        }
        if (!hasUsePermission(context.sender(), playerRef)) {
            Messages.noPerm(context, "/shop " + name);
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendKey(context, "shop.admin.ui_failed", java.util.Map.of());
            return;
        }
        PlayerShopBrowseUI ui = new PlayerShopBrowseUI(playerRef, shopManager, economy, config, shop, storage, draftCache);
        ui.open(player, ref, store);
    }

    private void openAdmin(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull String name) {
        ShopModel shop = shopManager.getShop(name);
        if (shop == null || !shop.isPlayerShop()) {
            Messages.sendKey(context, "shop.player.not_found", java.util.Map.of());
            return;
        }
        if (!hasEditAccess(context, playerRef, shop)) {
            Messages.noPerm(context, "/shop edit " + name);
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendKey(context, "shop.admin.ui_failed", java.util.Map.of());
            return;
        }
        draftCache.clear(playerRef.getUuid());
        ShopAdminUI ui = new ShopAdminUI(playerRef, shopManager, economy, shop, draftCache, storage, config);
        ui.open(player, ref, store);
    }

    private void linkChestForShop(@Nonnull CommandContext context,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull Ref<EntityStore> ref,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull World world,
                                  @Nonnull String name) {
        ShopModel shop = shopManager.getShop(name);
        if (shop == null || !shop.isPlayerShop()) {
            Messages.sendKey(context, "shop.player.not_found", java.util.Map.of());
            return;
        }
        if (!hasEditAccess(context, playerRef, shop)) {
            Messages.noPerm(context, "/shop " + name + " link");
            return;
        }
        int radius = config.getPlayerShopChestLinkRadius();
        Vector3i pos = ShopContainerUtil.findTargetedContainer(world, store, ref, Math.max(5, radius + 1));
        if (pos == null) {
            Messages.sendKey(context, "shop.player.chest.look_at", java.util.Map.of());
            return;
        }
        boolean already = shop.getChests().stream().anyMatch(chest ->
                chest != null
                        && chest.getWorldId().equalsIgnoreCase(world.getName())
                        && chest.getPosition().equals(pos));
        if (already) {
            Messages.sendKey(context, "shop.player.chest.already", java.util.Map.of());
            return;
        }
        if (!shop.getNpcs().isEmpty() && !ShopContainerUtil.isWithinRadius(pos, shop.getNpcs(), radius)) {
            Messages.sendKey(context, "shop.player.chest.too_far", java.util.Map.of());
            return;
        }
        shop.getChests().add(new xyz.thelegacyvoyage.hyessentialsx.models.ShopChestModel(pos, world.getName()));
        shopManager.saveShop(shop);
        Messages.sendKey(context, "shop.player.chest.linked", java.util.Map.of());
    }

    private void moveShopNpc(@Nonnull CommandContext context,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> ref,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull World world,
                             @Nonnull String name) {
        ShopModel shop = shopManager.getShop(name);
        if (shop == null || !shop.isPlayerShop()) {
            Messages.sendKey(context, "shop.player.not_found", java.util.Map.of());
            return;
        }
        if (!hasEditAccess(context, playerRef, shop)) {
            Messages.noPerm(context, "/shop " + name + " move");
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            Messages.sendKey(context, "shop.npc.player_pos_failed", java.util.Map.of());
            return;
        }
        Vector3d pos = transform.getPosition();
        if (!ShopPlacementUtil.canPlaceShop(playerRef, world, store, ref, pos)) {
            Messages.sendKey(context, "shop.player.claim_blocked", java.util.Map.of());
            return;
        }
        Vector3i basePos = new Vector3i(
                (int) Math.floor(pos.x()),
                (int) Math.floor(pos.y()),
                (int) Math.floor(pos.z())
        );
        Vector3d centerPos = new Vector3d(basePos.x() + 0.5D, basePos.y(), basePos.z() + 0.5D);
        com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation() != null ? transform.getRotation() : new com.hypixel.hytale.math.vector.Rotation3f(0f, 0f, 0f);

        ShopNpcModel targetNpc = null;
        for (ShopNpcModel npcModel : shop.getNpcs()) {
            if (npcModel == null) continue;
            if (!npcModel.getWorldId().equalsIgnoreCase(world.getName())) continue;
            targetNpc = npcModel;
            break;
        }
        ShopNpcEntityUtil.RoleSelection role = ShopNpcEntityUtil.resolveRoleSelection(shop.getNpcRole());
        if (role == null) {
            Messages.sendKey(context, "shop.npc.no_roles", java.util.Map.of());
            return;
        }
        ShopNpcEntityUtil.NpcLifecycleResult result = ShopNpcEntityUtil.moveOrSpawnShopNpc(
                world,
                store,
                shop,
                targetNpc,
                centerPos,
                rot,
                basePos,
                shop.getDisplayName(),
                playerRef.getUuid().toString(),
                playerRef.getUsername() == null ? "" : playerRef.getUsername(),
                role.roleName(),
                role.roleIndex()
        );
        if (!result.success()) {
            Messages.sendKey(context, "shop.npc.spawn_failed", java.util.Map.of());
            return;
        }
        pruneChestsOutOfRange(shop, world.getName(), basePos);
        shopManager.saveShop(shop);
        Messages.send(playerRef, "shop.player.npc.moved");
    }

    private void pruneChestsOutOfRange(@Nonnull ShopModel shop,
                                       @Nonnull String worldName,
                                       @Nonnull Vector3i basePos) {
        int radius = config.getPlayerShopChestLinkRadius();
        if (radius <= 0) {
            shop.getChests().clear();
            return;
        }
        shop.getChests().removeIf(chest -> {
            if (chest == null) return true;
            if (!worldName.equalsIgnoreCase(chest.getWorldId())) {
                return true;
            }
            Vector3i pos = chest.getPosition();
            if (pos == null) return true;
            double dx = Math.abs(pos.x() - basePos.x());
            double dy = Math.abs(pos.y() - basePos.y());
            double dz = Math.abs(pos.z() - basePos.z());
            return dx > radius || dy > radius || dz > radius;
        });
    }

    private boolean hasEditAccess(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull ShopModel shop) {
        if (hasNodePermission(context.sender(), playerRef, ADMIN_PERMISSION)) {
            return true;
        }
        if (isOwner(playerRef, shop)) {
            return true;
        }
        String uuid = playerRef.getUuid().toString();
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

    private boolean hasUsePermission(@Nonnull com.hypixel.hytale.server.core.command.system.CommandSender sender,
                                     @Nonnull PlayerRef playerRef) {
        return hasNodePermission(sender, playerRef, ADMIN_PERMISSION)
                || hasNodePermission(sender, playerRef, PERMISSION_NODE)
                || hasNodePermission(sender, playerRef, LEGACY_PERMISSION);
    }

    private boolean hasNodePermission(@Nonnull com.hypixel.hytale.server.core.command.system.CommandSender sender,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull String permission) {
        boolean senderHas = xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(sender, permission);
        boolean moduleHas = PermissionsModule.get().hasPermission(playerRef.getUuid(), permission, false);
        if (PermissionsModule.get().getFirstPermissionProvider() == null) {
            return senderHas || moduleHas;
        }
        return senderHas || moduleHas;
    }

    private boolean isOwner(@Nonnull PlayerRef playerRef, @Nonnull ShopModel shop) {
        String owner = shop.getOwnerUuid();
        return !owner.isBlank() && owner.equalsIgnoreCase(playerRef.getUuid().toString());
    }

    private int countShopsForOwner(@Nonnull String ownerUuid) {
        int total = 0;
        for (String name : shopManager.listPlayerShops()) {
            ShopModel shop = shopManager.getShop(name);
            if (shop == null || !shop.isPlayerShop()) continue;
            if (ownerUuid.equalsIgnoreCase(shop.getOwnerUuid())) {
                total++;
            }
        }
        return total;
    }

    private void spawnShopNpc(@Nonnull CommandContext context,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull World world,
                              @Nonnull ShopModel shop) {
        if (!shop.getNpcs().isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> playerEntityRef = world.getEntityStore().getRefFromUUID(playerRef.getUuid());
        if (playerEntityRef == null) {
            Messages.sendKey(context, "shop.npc.player_entity_missing", java.util.Map.of());
            return;
        }
        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            Messages.sendKey(context, "shop.npc.player_pos_failed", java.util.Map.of());
            return;
        }
        Vector3d playerPos = transform.getPosition();
        if (!ShopPlacementUtil.canPlaceShop(playerRef, world, store, playerEntityRef, playerPos)) {
            Messages.sendPrefixedKey(playerRef, "shop.player.claim_blocked", java.util.Map.of());
            return;
        }
        if (!chargeNpcCost(playerRef)) {
            return;
        }
        Vector3i basePos = new Vector3i(
                (int) Math.floor(playerPos.x()),
                (int) Math.floor(playerPos.y()),
                (int) Math.floor(playerPos.z())
        );
        Vector3d spawnPos = new Vector3d(basePos.x() + 0.5D, basePos.y(), basePos.z() + 0.5D);
        com.hypixel.hytale.math.vector.Rotation3f rotation = transform.getRotation() != null
                ? transform.getRotation()
                : new com.hypixel.hytale.math.vector.Rotation3f(0f, 0f, 0f);
        ShopNpcEntityUtil.RoleSelection role = ShopNpcEntityUtil.resolveRoleSelection(shop.getNpcRole());
        if (role == null) {
            Messages.sendKey(context, "shop.npc.no_roles", java.util.Map.of());
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
        if (result.success()) {
            shopManager.saveShop(shop);
        } else {
            Messages.sendKey(context, "shop.npc.spawn_failed", java.util.Map.of());
        }
    }

    private boolean chargeNpcCost(@Nonnull PlayerRef playerRef) {
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

    private void removeShopNpcs(@Nonnull ShopModel shop) {
        List<ShopNpcModel> npcs = List.copyOf(shop.getNpcs());
        for (World world : Universe.get().getWorlds().values()) {
            String worldName = world.getName();
            Store<EntityStore> store = world.getEntityStore().getStore();
            world.execute(() -> {
                for (ShopNpcModel npcModel : npcs) {
                    if (npcModel == null) continue;
                    if (!npcModel.getWorldId().equalsIgnoreCase(worldName)) continue;
                    ShopNpcEntityUtil.removeAllMatchingShopNpcs(store, shop, npcModel);
                }
                ShopNpcEntityUtil.removeAllShopNpcsInWorld(store, shop);
            });
        }
    }

    private void removeOrphanedShopNpcs(@Nonnull String displayName, @Nonnull String rawName) {
        for (World world : Universe.get().getWorlds().values()) {
            Store<EntityStore> store = world.getEntityStore().getStore();
            world.execute(() -> removeOrphanedShopNpcsInWorld(store, displayName, rawName));
        }
    }

    private void removeOrphanedShopNpcsInWorld(@Nonnull Store<EntityStore> store,
                                               @Nonnull String displayName,
                                               @Nonnull String rawName) {
        ShopNpcEntityUtil.removeShopNpcsByName(store, displayName, rawName);
    }

}

