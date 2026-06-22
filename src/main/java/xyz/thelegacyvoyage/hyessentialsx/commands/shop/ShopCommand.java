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
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.ShopAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.ui.ShopBrowseUI;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerCompatUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcEntityUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopNpcRemovalUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class ShopCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.adminshop.use";
    private static final String ADMIN_PERMISSION = "hyessentialsx.adminshop.admin";
    private static final String LEGACY_PERMISSION_NODE = "hyessentialsx.shop";
    private static final String LEGACY_ADMIN_PERMISSION = "hyessentialsx.shop.admin";

    private final ShopManager shopManager;
    private final EconomyManager economy;
    private final ShopAdminDraftCache draftCache;

    public ShopCommand(@Nonnull ShopManager shopManager,
                       @Nonnull EconomyManager economy,
                       @Nonnull ShopAdminDraftCache draftCache) {
        super("adminshop", "Open or manage admin shops");
        this.shopManager = shopManager;
        this.economy = economy;
        this.draftCache = draftCache;
        this.setPermissionGroups();
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new OpenSubCommand());
        this.addSubCommand(new CreateSubCommand());
        this.addSubCommand(new DeleteSubCommand());
        this.addSubCommand(new EditSubCommand());
        this.addSubCommand(new MoveSubCommand());
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
        if (!hasNodePermission(context.sender(), playerRef, PERMISSION_NODE)
                && !hasNodePermission(context.sender(), playerRef, LEGACY_PERMISSION_NODE)) {
            Messages.noPerm(context, "/adminshop");
            return;
        }
        listShops(context);
    }

    private final class ListSubCommand extends AbstractPlayerCommand {
        private ListSubCommand() {
            super("list", "List admin shops");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!hasNodePermission(context.sender(), playerRef, PERMISSION_NODE)
                    && !hasNodePermission(context.sender(), playerRef, LEGACY_PERMISSION_NODE)) {
                Messages.noPerm(context, "/adminshop list");
                return;
            }
            listShops(context);
        }
    }

    private final class OpenSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private OpenSubCommand() {
            super("open", "Open an admin shop");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
            this.addAliases(new String[]{"shop"});
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!hasNodePermission(context.sender(), playerRef, PERMISSION_NODE)
                    && !hasNodePermission(context.sender(), playerRef, LEGACY_PERMISSION_NODE)) {
                Messages.noPerm(context, "/adminshop open");
                return;
            }
            openBrowse(context, store, ref, playerRef, context.get(nameArg));
        }
    }

    private final class CreateSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private CreateSubCommand() {
            super("create", "Create an admin shop");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!hasNodePermission(context.sender(), playerRef, ADMIN_PERMISSION)
                    && !hasNodePermission(context.sender(), playerRef, LEGACY_ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/adminshop create");
                return;
            }
            ShopModel created = shopManager.createShop(context.get(nameArg));
            if (created == null) {
                Messages.sendKey(context, "shop.admin.exists_or_invalid", java.util.Map.of());
                return;
            }
            Messages.sendKey(context, "shop.admin.created", java.util.Map.of("shop", created.getDisplayName()));
            spawnShopNpc(context, playerRef, world, created);
        }
    }

    private final class DeleteSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private DeleteSubCommand() {
            super("delete", "Delete an admin shop");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
            this.addAliases(new String[]{"remove"});
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!hasNodePermission(context.sender(), playerRef, ADMIN_PERMISSION)
                    && !hasNodePermission(context.sender(), playerRef, LEGACY_ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/adminshop delete");
                return;
            }
            String name = context.get(nameArg);
            ShopModel shop = shopManager.getShop(name);
            if (shop != null && !shop.isPlayerShop()) {
                removeShopNpcs(shop);
                ShopNpcRemovalUtil.removeNearbyNpc(world, store, ref, shop);
            } else if (shop == null) {
                removeOrphanedShopNpcs(name, name);
            }
            if (shopManager.deleteShop(name) || shop == null) {
                Messages.sendKey(context, "shop.admin.deleted", java.util.Map.of());
            } else {
                Messages.sendKey(context, "shop.admin.not_found", java.util.Map.of());
            }
        }
    }

    private final class EditSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private EditSubCommand() {
            super("edit", "Edit an admin shop");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            openAdmin(context, store, ref, playerRef, context.get(nameArg));
        }
    }

    private final class MoveSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        private MoveSubCommand() {
            super("move", "Move an admin shop NPC");
            this.nameArg = withRequiredArg("name", "Shop name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            moveShopNpc(context, store, ref, playerRef, world, context.get(nameArg));
        }
    }

    private void listShops(@Nonnull CommandContext context) {
        List<String> names = shopManager.listAdminShops();
        if (names.isEmpty()) {
            Messages.sendKey(context, "shop.admin.none_configured", java.util.Map.of());
            return;
        }
        List<String> display = new ArrayList<>();
        for (String name : names) {
            ShopModel shop = shopManager.getShop(name);
            if (shop != null && !shop.getDisplayName().equalsIgnoreCase(name)) {
                display.add(name + " (" + shop.getDisplayName() + ")");
            } else {
                display.add(name);
            }
        }
        Messages.sendKey(context, "shop.admin.list", java.util.Map.of("shops", String.join(", ", display)));
    }

    private void openBrowse(@Nonnull CommandContext context,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull String name) {
        ShopModel shop = shopManager.getShop(name);
        if (shop == null || shop.isPlayerShop()) {
            Messages.sendKey(context, "shop.admin.not_found", java.util.Map.of());
            return;
        }
        if (!shop.getUsePermission().isBlank()
                && !hasNodePermission(context.sender(), playerRef, shop.getUsePermission())
                && !(shop.getUsePermission().equalsIgnoreCase(ShopManager.DEFAULT_USE_PERMISSION)
                && hasNodePermission(context.sender(), playerRef, ShopManager.LEGACY_USE_PERMISSION))) {
            Messages.noPerm(context, "/adminshop " + name);
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendKey(context, "shop.admin.ui_failed", java.util.Map.of());
            return;
        }
        ShopBrowseUI ui = new ShopBrowseUI(playerRef, shopManager, economy, shop, draftCache);
        ui.open(player, ref, store);
    }

    private void openAdmin(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull String name) {
        ShopModel shop = shopManager.getShop(name);
        if (shop == null || shop.isPlayerShop()) {
            Messages.sendKey(context, "shop.admin.not_found", java.util.Map.of());
            return;
        }
        if (!shop.getEditPermission().isBlank()
                && !hasNodePermission(context.sender(), playerRef, shop.getEditPermission())
                && !(shop.getEditPermission().equalsIgnoreCase(ShopManager.DEFAULT_EDIT_PERMISSION)
                && hasNodePermission(context.sender(), playerRef, ShopManager.LEGACY_EDIT_PERMISSION))) {
            Messages.noPerm(context, "/adminshop edit " + name);
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendKey(context, "shop.admin.ui_failed", java.util.Map.of());
            return;
        }
        draftCache.clear(playerRef.getUuid());
        ShopAdminUI ui = new ShopAdminUI(playerRef, shopManager, economy, shop, draftCache);
        ui.open(player, ref, store);
    }

    private void moveShopNpc(@Nonnull CommandContext context,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> ref,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull World world,
                             @Nonnull String name) {
        ShopModel shop = shopManager.getShop(name);
        if (shop == null || shop.isPlayerShop()) {
            Messages.sendKey(context, "shop.admin.not_found", java.util.Map.of());
            return;
        }
        if (!hasNodePermission(context.sender(), playerRef, ADMIN_PERMISSION)
                && !hasNodePermission(context.sender(), playerRef, LEGACY_ADMIN_PERMISSION)) {
            Messages.noPerm(context, "/adminshop " + name + " move");
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            Messages.sendKey(context, "shop.npc.player_pos_failed", java.util.Map.of());
            return;
        }
        Vector3d pos = transform.getPosition();
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
        shopManager.saveShop(shop);
        Messages.send(playerRef, "shop.admin.npc.moved");
    }

    private boolean hasNodePermission(@Nonnull com.hypixel.hytale.server.core.command.system.CommandSender sender,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull String permission) {
        boolean senderHas = xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(sender, permission);
        boolean moduleHas = PermissionsModule.get().hasPermission(playerRef.getUuid(), permission, false);
        return senderHas || moduleHas;
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

