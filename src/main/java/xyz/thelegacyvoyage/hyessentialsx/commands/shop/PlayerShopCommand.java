package xyz.thelegacyvoyage.hyessentialsx.commands.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.command.system.CommandContext;
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
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerCompatUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ShopContainerUtil;
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
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
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
        if (!config.isPlayerShopsEnabled()) {
            Messages.sendKey(context, "shop.player.disabled", java.util.Map.of());
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            if (!hasUsePermission(context.sender(), playerRef)) {
                Messages.noPerm(context, "/shop");
                return;
            }
            listShops(context, playerRef);
            return;
        }

        String sub = args.get(0).toLowerCase();
        if ("list".equals(sub)) {
            if (!hasUsePermission(context.sender(), playerRef)) {
                Messages.noPerm(context, "/shop");
                return;
            }
            listShops(context, playerRef);
            return;
        }

        if ("create".equals(sub)) {
            if (!hasPermission(context.sender(), playerRef, ADMIN_PERMISSION)
                    && !hasPermission(context.sender(), playerRef, CREATE_PERMISSION)) {
                Messages.noPerm(context, "/shop create");
                return;
            }
            if (args.size() < 2) {
                Messages.sendKey(context, "shop.player.usage.create", java.util.Map.of());
                return;
            }
            if (!hasPermission(context.sender(), playerRef, ADMIN_PERMISSION)) {
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
            String name = args.get(1);
            ShopModel created = shopManager.createPlayerShop(name, playerRef.getUuid().toString());
            if (created == null) {
                Messages.sendKey(context, "shop.admin.exists_or_invalid", java.util.Map.of());
                return;
            }
            Messages.sendKey(context, "shop.player.created", java.util.Map.of("shop", created.getDisplayName()));
            spawnShopNpc(context, playerRef, world, created);
            return;
        }

        if ("delete".equals(sub) || "remove".equals(sub)) {
            if (args.size() < 2) {
                Messages.sendKey(context, "shop.player.usage.delete", java.util.Map.of());
                return;
            }
            String name = args.get(1);
            ShopModel shop = shopManager.getShop(name);
            if (shop == null || !shop.isPlayerShop()) {
                Messages.sendKey(context, "shop.player.not_found", java.util.Map.of());
                return;
            }
            if (!hasPermission(context.sender(), playerRef, ADMIN_PERMISSION)
                    && !hasPermission(context.sender(), playerRef, DELETE_PERMISSION)
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
            return;
        }

        if ("edit".equals(sub)) {
            if (args.size() < 2) {
                Messages.sendKey(context, "shop.player.usage.edit", java.util.Map.of());
                return;
            }
            String name = args.get(1);
            openAdmin(context, store, ref, playerRef, name);
            return;
        }

        if ("move".equals(sub)) {
            if (args.size() < 2) {
                Messages.sendKey(context, "shop.player.usage.move", java.util.Map.of());
                return;
            }
            String name = args.get(1);
            moveShopNpc(context, store, ref, playerRef, world, name);
            return;
        }

        if (args.size() >= 2 && "link".equalsIgnoreCase(args.get(1))) {
            String name = args.get(0);
            linkChestForShop(context, store, ref, playerRef, world, name);
            return;
        }

        if (args.size() >= 2 && "move".equalsIgnoreCase(args.get(1))) {
            String name = args.get(0);
            moveShopNpc(context, store, ref, playerRef, world, name);
            return;
        }

        if (!hasUsePermission(context.sender(), playerRef)) {
            Messages.noPerm(context, "/shop " + args.get(0));
            return;
        }
        openBrowse(context, store, ref, playerRef, args.get(0));
    }

    private void listShops(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef) {
        boolean isAdmin = hasPermission(context.sender(), playerRef, ADMIN_PERMISSION);
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
                (int) Math.floor(pos.getX()),
                (int) Math.floor(pos.getY()),
                (int) Math.floor(pos.getZ())
        );
        Vector3d centerPos = new Vector3d(basePos.getX() + 0.5D, basePos.getY(), basePos.getZ() + 0.5D);
        Vector3f rot = transform.getRotation() != null ? transform.getRotation() : new Vector3f(0f, 0f, 0f);

        ShopNpcModel targetNpc = null;
        for (ShopNpcModel npcModel : shop.getNpcs()) {
            if (npcModel == null) continue;
            if (!npcModel.getWorldId().equalsIgnoreCase(world.getName())) continue;
            targetNpc = npcModel;
            break;
        }
        if (targetNpc == null) {
            spawnShopNpc(context, playerRef, world, shop);
            pruneChestsOutOfRange(shop, world.getName(), basePos);
            shopManager.saveShop(shop);
            return;
        }
        boolean moved = moveNpcEntity(world, store, targetNpc, centerPos, rot, basePos);
        if (!moved) {
            String targetId = targetNpc.getNpcId();
            shop.getNpcs().removeIf(npc -> npc != null && npc.getNpcId().equalsIgnoreCase(targetId));
            shopManager.saveShop(shop);
            spawnShopNpc(context, playerRef, world, shop);
            pruneChestsOutOfRange(shop, world.getName(), basePos);
            shopManager.saveShop(shop);
            return;
        }
        pruneChestsOutOfRange(shop, world.getName(), basePos);
        shopManager.saveShop(shop);
        Messages.send(playerRef, "shop.player.npc.moved");
    }

    private boolean moveNpcEntity(@Nonnull World world,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull ShopNpcModel npcModel,
                                  @Nonnull Vector3d position,
                                  @Nonnull Vector3f rotation,
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
            double dx = Math.abs(pos.getX() - basePos.getX());
            double dy = Math.abs(pos.getY() - basePos.getY());
            double dz = Math.abs(pos.getZ() - basePos.getZ());
            return dx > radius || dy > radius || dz > radius;
        });
    }

    private boolean hasEditAccess(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull ShopModel shop) {
        if (hasPermission(context.sender(), playerRef, ADMIN_PERMISSION)) {
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
        return hasPermission(sender, playerRef, ADMIN_PERMISSION)
                || hasPermission(sender, playerRef, PERMISSION_NODE)
                || hasPermission(sender, playerRef, LEGACY_PERMISSION);
    }

    private boolean hasPermission(@Nonnull com.hypixel.hytale.server.core.command.system.CommandSender sender,
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
        PlayerShopNpcCommand npcCommand = new PlayerShopNpcCommand(shopManager, economy, config);
        npcCommand.spawnNpcDirect(context, playerRef, world, shop.getName());
    }

    private void removeShopNpcs(@Nonnull ShopModel shop) {
        List<ShopNpcModel> npcs = List.copyOf(shop.getNpcs());
        for (World world : Universe.get().getWorlds().values()) {
            String worldName = world.getName();
            Store<EntityStore> store = world.getEntityStore().getStore();
            for (ShopNpcModel npcModel : npcs) {
                if (npcModel == null) continue;
                if (!npcModel.getWorldId().equalsIgnoreCase(worldName)) continue;
                world.execute(() -> {
                    boolean removed = despawnNpc(world, store, npcModel.getNpcId());
                    if (!removed) {
                        despawnNpcByPosition(world, store, npcModel.getPosition());
                    }
                });
            }
        }
        removeOrphanedShopNpcs(shop);
        shop.getNpcs().clear();
        shopManager.saveShop(shop);
    }

    private void removeOrphanedShopNpcs(@Nonnull ShopModel shop) {
        String displayName = shop.getDisplayName();
        String rawName = shop.getName();
        for (World world : Universe.get().getWorlds().values()) {
            Store<EntityStore> store = world.getEntityStore().getStore();
            world.execute(() -> store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
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
            }));
        }
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
}

