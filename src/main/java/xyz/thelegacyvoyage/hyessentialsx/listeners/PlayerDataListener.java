package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.AdminChatManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreecamManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.InfiniteStaminaManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MessageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SocialSpyManager;
import xyz.thelegacyvoyage.hyessentialsx.models.BanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.IpUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
public final class PlayerDataListener {

    private final StorageManager storage;
    private final BanManager bans;
    private final MessageManager messages;
    private final SocialSpyManager socialSpy;
    private final AdminChatManager adminChat;
    private final FreecamManager freecam;
    private final GodManager god;
    private final InfiniteStaminaManager stamina;
    private final FlyManager fly;
    private final xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager economy;
    private final xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager playtime;
    private final ConfigManager config;
    private final KitManager kits;
    private final Set<UUID> pendingStarterKitGrants = ConcurrentHashMap.newKeySet();

    public PlayerDataListener(@Nonnull StorageManager storage,
                              @Nonnull BanManager bans,
                              @Nonnull MessageManager messages,
                              @Nonnull SocialSpyManager socialSpy,
                              @Nonnull AdminChatManager adminChat,
                              @Nonnull FreecamManager freecam,
                              @Nonnull GodManager god,
                              @Nonnull InfiniteStaminaManager stamina,
                              @Nonnull FlyManager fly,
                              @Nonnull xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager economy,
                              @Nonnull xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager playtime,
                              @Nonnull ConfigManager config,
                              @Nonnull KitManager kits) {
        this.storage = storage;
        this.bans = bans;
        this.messages = messages;
        this.socialSpy = socialSpy;
        this.adminChat = adminChat;
        this.freecam = freecam;
        this.god = god;
        this.stamina = stamina;
        this.fly = fly;
        this.economy = economy;
        this.playtime = playtime;
        this.config = config;
        this.kits = kits;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerConnectEvent.class, event -> {
            PlayerRef player = event.getPlayerRef();
            if (player == null) return;

            PlayerDataModel data = storage.getPlayerData(player.getUuid());
            if (isFirstJoin(data) && !data.isStarterKitClaimed()) {
                pendingStarterKitGrants.add(player.getUuid());
            }

            economy.ensureStartingBalance(player.getUuid());
            storage.updatePlayerName(player.getUuid(), player.getUsername());
            playtime.onJoin(player.getUuid());
            god.clear(player.getUuid());
            stamina.clear(player.getUuid());

            if (data.getFirstJoinAt() <= 0L) {
                data.setFirstJoinAt(System.currentTimeMillis());
            }
            String ip = IpUtil.extractIp(player.getPacketHandler());
            if (ip != null && !ip.isBlank()) {
                data.addOrUpdateIp(ip);
            }
            storage.savePlayerDataAsync(player.getUuid(), data);
            fly.setFlySpeedMultiplier(player.getUuid(), data.getFlySpeedMultiplier());
            if (data.isFlyEnabled()) {
                fly.setEnabled(player.getUuid(), true);
                if (!fly.applyState(player, true)) {
                    fly.queueApply(player.getUuid());
                }
            }

            BanModel ban = bans.getBan(player.getUuid());
            if (ban != null) {
                String remaining = TimeUtil.formatRemaining(ban.getExpiresAt());
                String reason = (ban.getReason() != null && !ban.getReason().isBlank())
                        ? ban.getReason() : "Banned";
                player.getPacketHandler().disconnect(Messages.m("Banned: " + reason + " (" + remaining + ")"));
            }
        });

        events.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        events.registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef player = event.getPlayerRef();
            if (player == null) return;
            UUID uuid = player.getUuid();
            pendingStarterKitGrants.remove(uuid);

            PlayerDataModel data = storage.getPlayerData(uuid);
            data.setLastSeenAt(System.currentTimeMillis());
            data.setLastKnownName(player.getUsername());
            storage.savePlayerDataAsync(uuid, data);

            playtime.onQuit(uuid);
            messages.clear(uuid);
            socialSpy.clear(uuid);
            adminChat.clear(uuid);
            freecam.clear(uuid);
        });
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null) return;

        com.hypixel.hytale.server.core.entity.entities.Player playerEntity = event.getPlayer();
        if (playerEntity == null) return;
        com.hypixel.hytale.server.core.universe.world.World world = playerEntity.getWorld();
        if (world == null) return;

        world.execute(() -> {
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;
            UUID uuid = playerRef.getUuid();
            if (!pendingStarterKitGrants.contains(uuid)) return;

            PlayerDataModel data = storage.getPlayerData(uuid);
            if (data.isStarterKitClaimed()) {
                pendingStarterKitGrants.remove(uuid);
                return;
            }

            String defaultKit = config.getDefaultKitName().trim();
            if (defaultKit.isEmpty()) {
                pendingStarterKitGrants.remove(uuid);
                return;
            }

            KitModel kit = kits.getKit(defaultKit);
            if (kit == null) {
                Log.warn("Starter kit '" + defaultKit + "' was not found; skipping grant for " + playerRef.getUsername());
                pendingStarterKitGrants.remove(uuid);
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            Inventory inventory = player.getInventory();
            if (inventory == null) return;

            java.util.List<ItemStack> overflow = InventoryUtil.applyKit(inventory, kit.getItems());
            if (!overflow.isEmpty()) {
                dropOverflow(player, overflow);
            }

            data.setStarterKitClaimed(true);
            storage.savePlayerDataAsync(uuid, data);
            pendingStarterKitGrants.remove(uuid);
        });
    }

    private boolean isFirstJoin(@Nonnull PlayerDataModel data) {
        return data.getLastSeenAt() == 0L && data.getFirstJoinAt() <= 0L;
    }

    private void dropOverflow(@Nonnull Player player, @Nonnull java.util.List<ItemStack> overflow) {
        for (ItemStack stack : overflow) {
            if (stack == null || stack.isEmpty()) continue;
            if (!tryDropItem(player, stack)) {
                return;
            }
        }
    }

    private boolean tryDropItem(@Nonnull Player player, @Nonnull ItemStack stack) {
        String[] methods = {"dropItemStack", "dropItem", "dropItemAt", "spawnItemStack"};
        for (String name : methods) {
            for (Method method : player.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(ItemStack.class)) continue;
                try {
                    method.invoke(player, stack);
                    return true;
                } catch (Exception ignored) {
                }
            }
        }
        Log.warn("Failed to drop starter kit overflow items.");
        return false;
    }
}

