package xyz.thelegacyvoyage.hyessentialsx.commands.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
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
    private final OptionalArg<String> actionArg;
    private final OptionalArg<String> nameArg;

    public ShopCommand(@Nonnull ShopManager shopManager,
                       @Nonnull EconomyManager economy,
                       @Nonnull ShopAdminDraftCache draftCache) {
        super("adminshop", "Open or manage admin shops");
        this.shopManager = shopManager;
        this.economy = economy;
        this.draftCache = draftCache;
        this.setPermissionGroups();
        this.actionArg = withOptionalArg("action", "list, create, delete, edit, move, or shop", ArgTypes.STRING);
        this.nameArg = withOptionalArg("name", "Shop name", ArgTypes.STRING);
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
        List<String> args = new ArrayList<>();
        if (context.provided(actionArg)) args.add(context.get(actionArg));
        if (context.provided(nameArg)) args.add(context.get(nameArg));
        if (args.isEmpty()) {
            if (!hasPermission(context.sender(), playerRef, PERMISSION_NODE)
                    && !hasPermission(context.sender(), playerRef, LEGACY_PERMISSION_NODE)) {
                Messages.noPerm(context, "/adminshop");
                return;
            }
            listShops(context);
            return;
        }

        String sub = args.get(0).toLowerCase();
        if ("list".equals(sub)) {
            if (!hasPermission(context.sender(), playerRef, PERMISSION_NODE)
                    && !hasPermission(context.sender(), playerRef, LEGACY_PERMISSION_NODE)) {
                Messages.noPerm(context, "/adminshop");
                return;
            }
            listShops(context);
            return;
        }

        if ("create".equals(sub)) {
            if (!hasPermission(context.sender(), playerRef, ADMIN_PERMISSION)
                    && !hasPermission(context.sender(), playerRef, LEGACY_ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/adminshop create");
                return;
            }
            if (args.size() < 2) {
                Messages.sendKey(context, "shop.admin.usage.create", java.util.Map.of());
                return;
            }
            String name = args.get(1);
            ShopModel created = shopManager.createShop(name);
            if (created == null) {
                Messages.sendKey(context, "shop.admin.exists_or_invalid", java.util.Map.of());
                return;
            }
            Messages.sendKey(context, "shop.admin.created", java.util.Map.of("shop", created.getDisplayName()));
            spawnShopNpc(context, playerRef, world, created);
            return;
        }

        if ("delete".equals(sub) || "remove".equals(sub)) {
            if (!hasPermission(context.sender(), playerRef, ADMIN_PERMISSION)
                    && !hasPermission(context.sender(), playerRef, LEGACY_ADMIN_PERMISSION)) {
                Messages.noPerm(context, "/adminshop delete");
                return;
            }
            if (args.size() < 2) {
                Messages.sendKey(context, "shop.admin.usage.delete", java.util.Map.of());
                return;
            }
            String name = args.get(1);
            ShopModel shop = shopManager.getShop(name);
            if (shop != null && !shop.isPlayerShop()) {
                removeShopNpcs(shop);
                ShopNpcRemovalUtil.removeNearbyNpc(world, store, ref, shop);
            } else if (shop == null) {
                removeOrphanedShopNpcs(name, name);
            }
            if (shopManager.deleteShop(name)) {
                Messages.sendKey(context, "shop.admin.deleted", java.util.Map.of());
            } else if (shop == null) {
                Messages.sendKey(context, "shop.admin.deleted", java.util.Map.of());
            } else {
                Messages.sendKey(context, "shop.admin.not_found", java.util.Map.of());
            }
            return;
        }

        if ("edit".equals(sub)) {
            if (args.size() < 2) {
                Messages.sendKey(context, "shop.admin.usage.edit", java.util.Map.of());
                return;
            }
            String name = args.get(1);
            openAdmin(context, store, ref, playerRef, name);
            return;
        }

        if ("move".equals(sub)) {
            if (args.size() < 2) {
                Messages.sendKey(context, "shop.admin.usage.move", java.util.Map.of());
                return;
            }
            String name = args.get(1);
            moveShopNpc(context, store, ref, playerRef, world, name);
            return;
        }

        if (args.size() >= 2 && "move".equalsIgnoreCase(args.get(1))) {
            String name = args.get(0);
            moveShopNpc(context, store, ref, playerRef, world, name);
            return;
        }

        if (!hasPermission(context.sender(), playerRef, PERMISSION_NODE)
                && !hasPermission(context.sender(), playerRef, LEGACY_PERMISSION_NODE)) {
            Messages.noPerm(context, "/adminshop " + args.get(0));
            return;
        }
        openBrowse(context, store, ref, playerRef, args.get(0));
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
                && !hasPermission(context.sender(), playerRef, shop.getUsePermission())
                && !(shop.getUsePermission().equalsIgnoreCase(ShopManager.DEFAULT_USE_PERMISSION)
                && hasPermission(context.sender(), playerRef, ShopManager.LEGACY_USE_PERMISSION))) {
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
                && !hasPermission(context.sender(), playerRef, shop.getEditPermission())
                && !(shop.getEditPermission().equalsIgnoreCase(ShopManager.DEFAULT_EDIT_PERMISSION)
                && hasPermission(context.sender(), playerRef, ShopManager.LEGACY_EDIT_PERMISSION))) {
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
        if (!hasPermission(context.sender(), playerRef, ADMIN_PERMISSION)
                && !hasPermission(context.sender(), playerRef, LEGACY_ADMIN_PERMISSION)) {
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
        if (targetNpc == null) {
            spawnShopNpc(context, playerRef, world, shop);
            return;
        }
        boolean moved = moveNpcEntity(world, store, targetNpc, centerPos, rot, basePos);
        if (!moved) {
            String targetId = targetNpc.getNpcId();
            shop.getNpcs().removeIf(npc -> npc != null && npc.getNpcId().equalsIgnoreCase(targetId));
            shopManager.saveShop(shop);
            spawnShopNpc(context, playerRef, world, shop);
            return;
        }
        shopManager.saveShop(shop);
        Messages.send(playerRef, "shop.admin.npc.moved");
    }

    private boolean moveNpcEntity(@Nonnull World world,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull ShopNpcModel npcModel,
                                  @Nonnull Vector3d position,
                                  @Nonnull com.hypixel.hytale.math.vector.Rotation3f rotation,
                                  @Nonnull Vector3i basePos) {
        try {
            java.util.UUID npcUuid = java.util.UUID.fromString(npcModel.getNpcId());
            Ref<EntityStore> npcRef = world.getEntityRef(npcUuid);
            if (npcRef == null) return false;
            TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
            if (transform == null) return false;
            transform.setPosition(position);
            transform.setRotation(rotation);
            npcModel.setPosition(basePos);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasPermission(@Nonnull com.hypixel.hytale.server.core.command.system.CommandSender sender,
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
        ShopNpcCommand npcCommand = new ShopNpcCommand(shopManager);
        npcCommand.spawnNpcDirect(context, playerRef, world, shop.getName());
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
                    boolean removed = despawnNpc(world, store, npcModel.getNpcId());
                    if (!removed) {
                        despawnNpcByPosition(world, store, npcModel.getPosition());
                    }
                }
                removeOrphanedShopNpcsInWorld(store, shop.getDisplayName(), shop.getName());
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
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
                try {
                    Ref<EntityStore> ref = chunk.getReferenceTo(index);
                    NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                    if (npc == null) return;
                    Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
                    if (interactions == null) return;
                    String interactionId = interactions.getInteractionId(InteractionType.Use);
                    if (interactionId == null
                            || !interactionId.equalsIgnoreCase(ShopNpcInteractionRegistry.ADMIN_SHOP_ROOT_INTERACTION_ID)) {
                        return;
                    }
                    Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
                    if (nameplate == null) return;
                    String text = nameplate.getText();
                    if (text == null) return;
                    if (!text.equalsIgnoreCase(displayName) && !text.equalsIgnoreCase(rawName)) return;
                    npc.setToDespawn();
                    npc.setDespawning(true);
                    npc.setDespawnTime(0f);
                    npc.setDespawnRemainingSeconds(0f);
                    npc.setDespawnCheckRemainingSeconds(0f);
                    commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
                } catch (Exception ignored) {
                }
            });
    }

    private boolean despawnNpc(@Nonnull World world,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull String npcId) {
        try {
            java.util.UUID npcUuid = java.util.UUID.fromString(npcId);
            final boolean[] removed = {false};
            store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
                try {
                    Ref<EntityStore> ref = chunk.getReferenceTo(index);
                    NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                    if (npc != null && npcUuid.equals(ServerCompatUtil.getUuid(npc))) {
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

    private boolean despawnNpcByPosition(@Nonnull World world,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull Vector3i position) {
        final boolean[] removed = {false};
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc == null) return;
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null || transform.getPosition() == null) return;
                Vector3d pos = transform.getPosition();
                double dx = Math.abs(pos.x() - (position.x() + 0.5D));
                double dy = Math.abs(pos.y() - position.y());
                double dz = Math.abs(pos.z() - (position.z() + 0.5D));
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
}

