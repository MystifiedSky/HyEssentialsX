package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.ui.PlayerShopBrowseUI;
import xyz.thelegacyvoyage.hyessentialsx.ui.ShopBrowseUI;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerCompatUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopNpcInteractionRegistry {

    public static final String ADMIN_SHOP_ROOT_INTERACTION_ID = "hyessentialsx/admin_shop";
    private static final String ADMIN_SHOP_INTERACTION_ID = "hyessentialsx/open_admin_shop_ui";
    private static final String PLAYER_SHOP_USE_PERMISSION = "hyessentialsx.playershop.use";
    private static final String PLAYER_SHOP_LEGACY_PERMISSION = "hyessentialsx.playershop";
    private static final String PLAYER_SHOP_ADMIN_PERMISSION = "hyessentialsx.playershop.admin";
    private static final long INTERACTION_COOLDOWN_MS = 500L;

    private static volatile boolean registered;
    private static ShopManager shopManager;
    private static EconomyManager economyManager;
    private static ConfigManager configManager;
    private static ShopAdminDraftCache draftCache;
    private static Field interactionIdField;
    private static Field customPageSupplierField;
    private static PacketFilter packetFilter;
    private static final Map<UUID, Long> interactionCooldowns = new ConcurrentHashMap<>();

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
        packetFilter = PacketAdapters.registerInbound(new ShopNpcPacketWatcher());
    }

    public static void unregister() {
        if (packetFilter != null) {
            PacketAdapters.deregisterInbound(packetFilter);
            packetFilter = null;
        }
        interactionCooldowns.clear();
        registered = false;
    }

    public static void applyNpcInteractions(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
        try {
            Object interactions = store.getComponent(npcRef, com.hypixel.hytale.server.core.modules.interaction.Interactions.getComponentType());
            if (interactions != null) {
                store.removeComponent(npcRef, com.hypixel.hytale.server.core.modules.interaction.Interactions.getComponentType());
            }
        } catch (Exception ignored) {
        }
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
        if (configManager != null && !configManager.isAdminShopsEnabled()) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.disabled", java.util.Map.of());
            return null;
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
    private static CustomUIPage createPageForNpc(@Nonnull Ref<EntityStore> playerEntityRef,
                                                 @Nonnull Store<EntityStore> store,
                                                 @Nonnull PlayerRef playerRef,
                                                 @Nonnull Ref<EntityStore> targetRef) {
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
        if (configManager != null && !configManager.isAdminShopsEnabled()) {
            Messages.sendPrefixedKey(playerRef, "shop.admin.disabled", java.util.Map.of());
            return null;
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
        java.util.UUID npcUuid = ServerCompatUtil.getUuid(npc);
        if (npcUuid != null) {
            ShopModel byId = shopManager.findShopByNpcId(npcUuid.toString());
            if (byId != null) {
                return byId;
            }
        }
        return resolveShopForNpcFallback(store, npcRef, npc);
    }

    @Nullable
    private static ShopModel resolveShopForNpcFallback(@Nonnull Store<EntityStore> store,
                                                       @Nonnull Ref<EntityStore> npcRef,
                                                       @Nonnull NPCEntity npc) {
        TransformComponent transform = store.getComponent(npcRef, TransformComponent.getComponentType());
        org.joml.Vector3d pos = transform != null ? transform.getPosition() : null;
        if (pos == null) {
            try {
                pos = npc.getLeashPoint();
            } catch (Exception ignored) {
            }
        }
        String nameplate = getNameplate(store, npcRef);
        String worldName = npc.getWorld() != null ? npc.getWorld().getName() : "";
        ShopModel best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ShopNpcModel model : shopManager.listAllNpcs()) {
            if (!worldName.isBlank() && !model.getWorldId().equalsIgnoreCase(worldName)) {
                continue;
            }
            ShopModel shop = shopManager.getShop(model.getShopName());
            if (shop == null || !matchesShopName(nameplate, shop)) {
                continue;
            }
            double distance = pos == null ? 0.0D : distanceSquared(pos, model.getPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = shop;
            }
        }
        return bestDistance <= 25.0D ? best : null;
    }

    @Nullable
    private static String getNameplate(@Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> npcRef) {
        Nameplate nameplate = store.getComponent(npcRef, Nameplate.getComponentType());
        return nameplate == null ? null : nameplate.getText();
    }

    private static boolean matchesShopName(@Nullable String nameplate, @Nonnull ShopModel shop) {
        if (nameplate == null || nameplate.isBlank()) {
            return false;
        }
        return nameplate.equalsIgnoreCase(shop.getName()) || nameplate.equalsIgnoreCase(shop.getDisplayName());
    }

    private static double distanceSquared(@Nonnull org.joml.Vector3d pos,
                                          @Nonnull org.joml.Vector3i blockPos) {
        double dx = pos.x() - (blockPos.x() + 0.5D);
        double dy = pos.y() - blockPos.y();
        double dz = pos.z() - (blockPos.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean hasPermission(@Nonnull Store<EntityStore> store,
                                         @Nonnull Ref<EntityStore> playerEntityRef,
                                         @Nonnull PlayerRef playerRef,
                                         @Nonnull String permission) {
        return PermissionsModule.get().hasPermission(playerRef.getUuid(), permission, false);
    }

    private static boolean checkCooldown(@Nonnull UUID playerId) {
        long now = System.currentTimeMillis();
        Long last = interactionCooldowns.get(playerId);
        if (last != null && now - last < INTERACTION_COOLDOWN_MS) {
            return false;
        }
        interactionCooldowns.put(playerId, now);
        return true;
    }

    private static final class ShopNpcPacketWatcher implements PacketWatcher {
        @Override
        public void accept(PacketHandler handler, Packet packet) {
            if (!(packet instanceof SyncInteractionChains chains) || chains.updates == null) {
                return;
            }
            if (handler.getAuth() == null) {
                return;
            }
            UUID playerId = handler.getAuth().getUuid();
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null || !playerRef.isValid() || playerRef.getReference() == null) {
                return;
            }
            var world = Universe.get().getWorld(playerRef.getWorldUuid());
            if (world == null) {
                return;
            }
            world.execute(() -> handleChains(playerId, playerRef, chains.updates));
        }

        private void handleChains(@Nonnull UUID playerId,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull SyncInteractionChain[] chains) {
            for (SyncInteractionChain chain : chains) {
                if (chain == null || chain.interactionType != InteractionType.Use || chain.data == null) {
                    continue;
                }
                if (handleUse(playerId, playerRef, chain.data.entityId)) {
                    return;
                }
            }
        }

        private boolean handleUse(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef, int entityId) {
            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return false;
            }
            Store<EntityStore> store = playerEntityRef.getStore();
            Object external = store.getExternalData();
            if (!(external instanceof EntityStore entityStore)) {
                return false;
            }
            Ref<EntityStore> targetRef = entityStore.getRefFromNetworkId(entityId);
            if (targetRef == null || !targetRef.isValid()) {
                return false;
            }
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) {
                return false;
            }
            NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
            if (npc == null) {
                return false;
            }
            ShopModel shop = resolveShopForNpc(store, targetRef, npc);
            if (shop == null) {
                return false;
            }
            if (!checkCooldown(playerId)) {
                return true;
            }
            CustomUIPage page = createPageForNpc(playerEntityRef, store, playerRef, targetRef);
            if (page == null) {
                return true;
            }
            player.getPageManager().openCustomPage(playerEntityRef, store, page);
            return true;
        }
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

