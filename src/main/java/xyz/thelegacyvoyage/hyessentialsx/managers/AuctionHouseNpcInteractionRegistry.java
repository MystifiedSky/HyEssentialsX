package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import xyz.thelegacyvoyage.hyessentialsx.ui.AuctionHouseUI;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerCompatUtil;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionHouseNpcInteractionRegistry {

    private static final String USE_PERMISSION = "hyessentialsx.auctionhouse.use";
    private static final long INTERACTION_COOLDOWN_MS = 500L;

    private static AuctionHouseManager manager;
    private static EconomyManager economy;
    private static ConfigManager config;
    private static PacketFilter packetFilter;
    private static final Map<UUID, Long> interactionCooldowns = new ConcurrentHashMap<>();

    private AuctionHouseNpcInteractionRegistry() {
    }

    public static void register(@Nonnull AuctionHouseManager manager,
                                @Nonnull EconomyManager economy,
                                @Nonnull ConfigManager config) {
        AuctionHouseNpcInteractionRegistry.manager = manager;
        AuctionHouseNpcInteractionRegistry.economy = economy;
        AuctionHouseNpcInteractionRegistry.config = config;
        if (packetFilter == null) {
            packetFilter = PacketAdapters.registerInbound(new AuctionHouseNpcPacketWatcher());
        }
    }

    public static void unregister() {
        if (packetFilter != null) {
            PacketAdapters.deregisterInbound(packetFilter);
            packetFilter = null;
        }
        interactionCooldowns.clear();
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

    private static final class AuctionHouseNpcPacketWatcher implements PacketWatcher {
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
            if (manager == null || economy == null || config == null || !config.isAuctionHouseEnabled()) {
                return false;
            }
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
            NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
            if (player == null || npc == null) {
                return false;
            }
            UUID npcId = ServerCompatUtil.getUuid(npc);
            if (npcId == null || manager.findNpcById(npcId.toString()) == null) {
                return false;
            }
            if (!checkCooldown(playerId)) {
                return true;
            }
            if (config.isUsePermissionsSystem()
                    && !PermissionsModule.get().hasPermission(playerRef.getUuid(), USE_PERMISSION, false)) {
                Messages.sendPrefixedKey(playerRef, "auction.no_permission", Map.of());
                return true;
            }
            CustomUIPage page = new AuctionHouseUI(playerRef, manager, economy, config);
            player.getPageManager().openCustomPage(playerEntityRef, store, page);
            return true;
        }
    }
}
