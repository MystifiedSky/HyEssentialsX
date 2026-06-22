package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.IpBanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.IpUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public final class IpBanListener {

    private final IpBanManager ipBans;
    private final StorageManager storage;

    public IpBanListener(@Nonnull IpBanManager ipBans, @Nonnull StorageManager storage) {
        this.ipBans = ipBans;
        this.storage = storage;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(PlayerSetupConnectEvent.class, this::onSetup);
    }

    private void onSetup(@Nonnull PlayerSetupConnectEvent event) {
        String ip = IpUtil.extractIp(event.getPacketHandler());
        if (ip != null && event.getUuid() != null) {
            UUID uuid = event.getUuid();
            PlayerDataModel data = storage.getPlayerData(uuid);
            data.addOrUpdateIp(ip);
            storage.savePlayerDataAsync(uuid, data);
        }

        if (ip == null) return;
        IpBanModel ban = ipBans.getBan(ip);
        if (ban == null) return;

        String reason = ban.getReason();
        String msg = Messages.tr(null, "ipban.blocked", Map.of(
                "reason", (reason != null && !reason.isBlank()) ? reason : "IP banned"
        ));
        event.setReason(msg);
        event.setCancelled(true);
    }
}

