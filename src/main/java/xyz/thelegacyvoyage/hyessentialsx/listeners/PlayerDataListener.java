package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.AdminChatManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreecamManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.InfiniteStaminaManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MessageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.BanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.IpUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class PlayerDataListener {

    private final StorageManager storage;
    private final BanManager bans;
    private final MessageManager messages;
    private final AdminChatManager adminChat;
    private final FreecamManager freecam;
    private final GodManager god;
    private final InfiniteStaminaManager stamina;
    private final FlyManager fly;
    private final xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager economy;
    private final xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager playtime;

    public PlayerDataListener(@Nonnull StorageManager storage,
                              @Nonnull BanManager bans,
                              @Nonnull MessageManager messages,
                              @Nonnull AdminChatManager adminChat,
                              @Nonnull FreecamManager freecam,
                              @Nonnull GodManager god,
                              @Nonnull InfiniteStaminaManager stamina,
                              @Nonnull FlyManager fly,
                              @Nonnull xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager economy,
                              @Nonnull xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager playtime) {
        this.storage = storage;
        this.bans = bans;
        this.messages = messages;
        this.adminChat = adminChat;
        this.freecam = freecam;
        this.god = god;
        this.stamina = stamina;
        this.fly = fly;
        this.economy = economy;
        this.playtime = playtime;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerConnectEvent.class, event -> {
            PlayerRef player = event.getPlayerRef();
            if (player == null) return;

            economy.ensureStartingBalance(player.getUuid());
            storage.updatePlayerName(player.getUuid(), player.getUsername());
            playtime.onJoin(player.getUuid());
            god.clear(player.getUuid());
            stamina.clear(player.getUuid());

            PlayerDataModel data = storage.getPlayerData(player.getUuid());
            String ip = IpUtil.extractIp(player.getPacketHandler());
            if (ip != null && !ip.isBlank()) {
                data.addOrUpdateIp(ip);
                storage.savePlayerDataAsync(player.getUuid(), data);
            }
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
                player.getPacketHandler().disconnect("Banned: " + reason + " (" + remaining + ")");
            }
        });

        events.registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef player = event.getPlayerRef();
            if (player == null) return;
            UUID uuid = player.getUuid();

            PlayerDataModel data = storage.getPlayerData(uuid);
            data.setLastSeenAt(System.currentTimeMillis());
            data.setLastKnownName(player.getUsername());
            storage.savePlayerDataAsync(uuid, data);

            playtime.onQuit(uuid);
            messages.clear(uuid);
            adminChat.clear(uuid);
            freecam.clear(uuid);
        });
    }
}

