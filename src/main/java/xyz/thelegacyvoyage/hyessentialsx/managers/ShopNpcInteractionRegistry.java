package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.ui.PlayerShopBrowseUI;
import xyz.thelegacyvoyage.hyessentialsx.ui.ShopBrowseUI;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;

public final class ShopNpcInteractionRegistry {

    public static final String ADMIN_SHOP_ROOT_INTERACTION_ID = "hyessentialsx/admin_shop";
    private static final String ADMIN_SHOP_INTERACTION_ID = "hyessentialsx/open_admin_shop_ui";
    private static final String ADMIN_SHOP_HINT = "Press F to open shop";
    private static final double FALLBACK_DISTANCE_SQ = 2.25D;
    private static final String PLAYER_SHOP_USE_PERMISSION = "hyessentialsx.playershop.use";
    private static final String PLAYER_SHOP_LEGACY_PERMISSION = "hyessentialsx.playershop";
    private static final String PLAYER_SHOP_ADMIN_PERMISSION = "hyessentialsx.playershop.admin";

    private static volatile boolean registered;
    private static ShopManager shopManager;
    private static EconomyManager economyManager;
    private static ConfigManager configManager;
    private static ShopAdminDraftCache draftCache;
    private static Field interactionIdField;
    private static Field customPageSupplierField;

    private ShopNpcInteractionRegistry() {
    }

    public static void register(@Nonnull HyEssentialsXPlugin plugin,
                                @Nonnull ShopManager shopManager,
                                @Nonnull EconomyManager economyManager,
                                @Nonnull ConfigManager configManager,
                                @Nonnull ShopAdminDraftCache draftCache) {
        ShopNpcInteractionRegistry.shopManager = shopManager;
        ShopNpcInteractionRegistry.economyManager = economyManager;
        ShopNpcInteractionRegistry.configManager = configManager;
        ShopNpcInteractionRegistry.draftCache = draftCache;
        if (registered) {
            return;
        }
        registered = true;
        ensureInteractionAssets();
    }

    public static void applyNpcInteractions(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
        Interactions interactions = store.getComponent(npcRef, Interactions.getComponentType());
        if (interactions == null) {
            interactions = new Interactions();
            store.addComponent(npcRef, Interactions.getComponentType(), interactions);
        }
        interactions.setInteractionId(InteractionType.Use, ADMIN_SHOP_ROOT_INTERACTION_ID);
        interactions.setInteractionHint(ADMIN_SHOP_HINT);
    }

    private static void ensureInteractionAssets() {
        OpenCustomUIInteraction interaction = null;
        Interaction existing = Interaction.getAssetMap().getAsset(ADMIN_SHOP_INTERACTION_ID);
        if (existing instanceof OpenCustomUIInteraction open) {
            interaction = open;
        }
        if (interaction == null) {
            interaction = new OpenCustomUIInteraction();
            setInteractionId(interaction, ADMIN_SHOP_INTERACTION_ID);
            Interaction.getAssetStore().loadAssets("HyEssentialsX", List.of(interaction));
        }
        setCustomPageSupplier(interaction, ShopNpcInteractionRegistry::createPageForInteraction);

        RootInteraction existingRoot = RootInteraction.getAssetMap().getAsset(ADMIN_SHOP_ROOT_INTERACTION_ID);
        if (existingRoot == null) {
            RootInteraction root = new RootInteraction(ADMIN_SHOP_ROOT_INTERACTION_ID, ADMIN_SHOP_INTERACTION_ID);
            root.build();
            RootInteraction.getAssetStore().loadAssets("HyEssentialsX", List.of(root));
        }
    }

    @Nullable
    private static CustomUIPage createPageForInteraction(@Nonnull Ref<EntityStore> playerEntityRef,
                                                         @Nonnull ComponentAccessor<EntityStore> accessor,
                                                         @Nonnull PlayerRef playerRef,
                                                         @Nonnull com.hypixel.hytale.server.core.entity.InteractionContext context) {
        Store<EntityStore> store = resolveStore(accessor);
        if (store == null) {
            return null;
        }
        Ref<EntityStore> targetRef = resolveTargetRef(context, store);
        if (targetRef == null) {
            return null;
        }
        NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            return null;
        }
        ShopModel shop = resolveShopForNpc(store, targetRef, npc);
        if (shop == null) {
            return null;
        }
        if (shop.isPlayerShop()) {
            if (!hasPermission(store, playerEntityRef, playerRef, PLAYER_SHOP_ADMIN_PERMISSION)
                    && !hasPermission(store, playerEntityRef, playerRef, PLAYER_SHOP_USE_PERMISSION)
                    && !hasPermission(store, playerEntityRef, playerRef, PLAYER_SHOP_LEGACY_PERMISSION)) {
                Messages.sendPrefixedKey(playerRef, "shop.use.no_permission", java.util.Map.of());
                return null;
            }
            if (configManager != null && !configManager.isPlayerShopsEnabled()) {
                Messages.sendPrefixedKey(playerRef, "shop.player.disabled", java.util.Map.of());
                return null;
            }
            return new PlayerShopBrowseUI(playerRef, shopManager, economyManager, configManager, shop, shopManager.getStorage(), draftCache);
        }
        if (!shop.getUsePermission().isBlank()
                && !hasPermission(store, playerEntityRef, playerRef, shop.getUsePermission())
                && !(shop.getUsePermission().equalsIgnoreCase(ShopManager.DEFAULT_USE_PERMISSION)
                && hasPermission(store, playerEntityRef, playerRef, ShopManager.LEGACY_USE_PERMISSION))) {
            Messages.sendPrefixedKey(playerRef, "shop.use.no_permission", java.util.Map.of());
            return null;
        }
        return new ShopBrowseUI(playerRef, shopManager, economyManager, shop, draftCache);
    }

    @Nullable
    private static Store<EntityStore> resolveStore(@Nonnull ComponentAccessor<EntityStore> accessor) {
        if (accessor instanceof CommandBuffer<EntityStore> buffer) {
            return buffer.getStore();
        }
        if (accessor instanceof Store) {
            @SuppressWarnings("unchecked")
            Store<EntityStore> store = (Store<EntityStore>) accessor;
            return store;
        }
        return null;
    }

    @Nullable
    private static Ref<EntityStore> resolveTargetRef(@Nonnull com.hypixel.hytale.server.core.entity.InteractionContext context,
                                                     @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> target = context.getTargetEntity();
        if (target != null) {
            return target;
        }
        InteractionSyncData syncData = context.getClientState();
        if (syncData == null) {
            return null;
        }
        Object external = store.getExternalData();
        if (!(external instanceof EntityStore entityStore)) {
            return null;
        }
        return entityStore.getRefFromNetworkId(syncData.entityId);
    }

    @Nullable
    private static ShopModel resolveShopForNpc(@Nonnull Store<EntityStore> store,
                                               @Nonnull Ref<EntityStore> npcRef,
                                               @Nonnull NPCEntity npc) {
        ShopModel byId = shopManager.findShopByNpcId(npc.getUuid().toString());
        if (byId != null) {
            return byId;
        }
        Vector3d pos = npc.getLeashPoint();
        if (pos == null) {
            var transform = store.getComponent(npcRef, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform != null) {
                pos = transform.getPosition();
            }
        }
        if (pos == null) {
            return null;
        }
        String worldName = "";
        Object external = store.getExternalData();
        if (external instanceof EntityStore entityStore && entityStore.getWorld() != null) {
            worldName = entityStore.getWorld().getName();
        }
        ShopNpcModel best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (ShopNpcModel npcModel : shopManager.listAllNpcs()) {
            if (!worldName.isBlank() && !npcModel.getWorldId().equalsIgnoreCase(worldName)) {
                continue;
            }
            var p = npcModel.getPosition();
            double dx = pos.getX() - (p.getX() + 0.5D);
            double dy = pos.getY() - p.getY();
            double dz = pos.getZ() - (p.getZ() + 0.5D);
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = npcModel;
            }
        }
        if (best == null || bestDistSq > FALLBACK_DISTANCE_SQ) {
            return null;
        }
        return shopManager.getShop(best.getShopName());
    }

    private static boolean hasPermission(@Nonnull Store<EntityStore> store,
                                         @Nonnull Ref<EntityStore> playerEntityRef,
                                         @Nonnull PlayerRef playerRef,
                                         @Nonnull String permission) {
        Boolean componentHas = null;
        try {
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player != null) {
                componentHas = player.hasPermission(permission);
            }
        } catch (Exception ignored) {
        }
        boolean moduleHas = PermissionsModule.get().hasPermission(playerRef.getUuid(), permission, false);
        if (PermissionsModule.get().getFirstPermissionProvider() == null) {
            if (componentHas != null) {
                return componentHas;
            }
            return moduleHas;
        }
        if (componentHas == null) {
            return moduleHas;
        }
        return moduleHas || componentHas;
    }

    private static void setInteractionId(@Nonnull Interaction interaction, @Nonnull String id) {
        try {
            if (interactionIdField == null) {
                interactionIdField = Interaction.class.getDeclaredField("id");
                interactionIdField.setAccessible(true);
            }
            interactionIdField.set(interaction, id);
        } catch (Exception e) {
            Log.warn("[ShopNPC] Failed to set interaction id: " + e.getMessage());
        }
    }

    private static void setCustomPageSupplier(@Nonnull OpenCustomUIInteraction interaction,
                                              @Nonnull OpenCustomUIInteraction.CustomPageSupplier supplier) {
        try {
            if (customPageSupplierField == null) {
                customPageSupplierField =
                        OpenCustomUIInteraction.class.getDeclaredField("customPageSupplier");
                customPageSupplierField.setAccessible(true);
            }
            customPageSupplierField.set(interaction, supplier);
        } catch (Exception e) {
            Log.warn("[ShopNPC] Failed to set custom page supplier: " + e.getMessage());
        }
    }
}

