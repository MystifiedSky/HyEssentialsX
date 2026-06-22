package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.IpBanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.BanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.VanillaBanUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BanListUI extends InteractiveCustomUIPage<BanListUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/BanListPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/BanRow.ui";
    private static final String UNBAN_PERMISSION = "hyessentialsx.unban";
    private static final String IPBAN_PERMISSION = "hyessentialsx.ipban";
    private static final int REASON_MAX_LENGTH = 140;

    private final PlayerRef playerRef;
    private final BanManager banManager;
    private final IpBanManager ipBanManager;
    private final StorageManager storage;

    public BanListUI(@Nonnull PlayerRef playerRef,
                     @Nonnull BanManager banManager,
                     @Nonnull IpBanManager ipBanManager,
                     @Nonnull StorageManager storage) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.banManager = banManager;
        this.ipBanManager = ipBanManager;
        this.storage = storage;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        List<BanEntry> bans = collectBans();
        cmd.set("#BanCount.Text", bans.size() + " Bans");
        buildBanList(cmd, evt, bans);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "Close"),
                false
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UIEventData data
    ) {
        if (data.action == null || data.action.isEmpty()) {
            return;
        }
        if (data.action.equals("Close")) {
            close();
            return;
        }

        if (data.action.equals("Unban")) {
            handleUnban(ref, store, data.target);
            return;
        }

        if (data.action.equals("UnipBan")) {
            handleUnipBan(ref, store, data.target);
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void buildBanList(@Nonnull UICommandBuilder cmd,
                              @Nonnull UIEventBuilder evt,
                              @Nonnull List<BanEntry> bans) {
        cmd.clear("#BanList");

        if (bans.isEmpty()) {
            cmd.appendInline("#BanList",
                    "Label { Text: \"No banned players.\"; " +
                            "Style: (FontSize: 13, TextColor: #666666, HorizontalAlignment: Center); " +
                            "Anchor: (Top: 30); }");
            return;
        }

        int rowIndex = 0;
        for (BanEntry entry : bans) {
            cmd.appendInline("#BanList", "Group { Anchor: (Bottom: 6); }");
            cmd.append("#BanList[" + rowIndex + "]", ROW_LAYOUT);
            String rowBase = "#BanList[" + rowIndex + "][0]";
            cmd.set(rowBase + " #Info #PlayerName.Text", entry.displayName);
            cmd.set(rowBase + " #Info #BanReason.Text", entry.reason);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    rowBase + " #Actions #UnbanBtn",
                    EventData.of("Action", entry.action).append("Target", entry.target),
                    false
            );
            rowIndex++;
        }
    }

    private void handleUnban(@Nonnull Ref<EntityStore> ref,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull String uuidText) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch (Exception e) {
            return;
        }

        if (!CommandPermissionUtil.hasPermission(playerRef, UNBAN_PERMISSION)) {
            Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of("command", "/unban"));
            return;
        }

        BanModel ban = banManager.getBan(uuid);
        String name = resolveName(uuid, ban);
        banManager.unban(uuid);
        VanillaBanUtil.unbanVanilla(uuid);
        Messages.sendPrefixedKey(playerRef, "ban.unbanned", Map.of("player", name));

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        BanListUI page = new BanListUI(playerRef, banManager, ipBanManager, storage);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void handleUnipBan(@Nonnull Ref<EntityStore> ref,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull String ip) {
        if (!CommandPermissionUtil.hasPermission(playerRef, IPBAN_PERMISSION)) {
            Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of("command", "/unipban"));
            return;
        }

        boolean removed = ipBanManager.unban(ip);
        if (!removed) {
            Messages.sendPrefixedKey(playerRef, "ipban.not_banned", Map.of());
            return;
        }
        Messages.sendPrefixedKey(playerRef, "ipban.unbanned", Map.of("ip", ip));

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        BanListUI page = new BanListUI(playerRef, banManager, ipBanManager, storage);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private List<BanEntry> collectBans() {
        List<BanEntry> out = new ArrayList<>();
        for (UUID uuid : storage.listPlayerIds()) {
            BanModel ban = banManager.getBan(uuid);
            if (ban == null) {
                continue;
            }
            String name = resolveName(uuid, ban);
            String reason = resolveReason(ban);
            out.add(BanEntry.player(uuid, name, reason));
        }
        if (CommandPermissionUtil.hasPermission(playerRef, IPBAN_PERMISSION)) {
            for (IpBanModel ipBan : storage.getIpBans().values()) {
                if (ipBan == null) continue;
                String ip = ipBan.getIp();
                if (ip == null || ip.isBlank()) continue;
                String displayName = resolveIpDisplayName(ipBan, ip);
                String reason = resolveIpReason(ipBan, ip);
                out.add(BanEntry.ip(ip, displayName, reason));
            }
        }
        out.sort(Comparator.comparing(entry -> entry.displayName.toLowerCase(Locale.ROOT)));
        return out;
    }

    private String resolveName(@Nonnull UUID uuid, BanModel ban) {
        String name = null;
        if (ban != null) {
            name = ban.getPlayerName();
        }
        if (name == null || name.isBlank()) {
            PlayerDataModel data = storage.getPlayerData(uuid);
            if (data != null) {
                name = data.getLastKnownName();
            }
        }
        if (name == null || name.isBlank()) {
            name = uuid.toString();
        }
        return name;
    }

    private String resolveReason(@Nonnull BanModel ban) {
        String reason = ban.getReason();
        if (reason == null || reason.isBlank()) {
            reason = Messages.tr(playerRef, "reason.none", Map.of());
        }
        return truncate(reason);
    }

    private String resolveIpDisplayName(@Nonnull IpBanModel ban, @Nonnull String ip) {
        String name = ban.getPlayerName();
        if (name == null || name.isBlank()) {
            return "IP: " + ip;
        }
        return name;
    }

    private String resolveIpReason(@Nonnull IpBanModel ban, @Nonnull String ip) {
        String reason = ban.getReason();
        if (reason == null || reason.isBlank()) {
            reason = Messages.tr(playerRef, "reason.none", Map.of());
        }
        String playerName = ban.getPlayerName();
        if (playerName != null && !playerName.isBlank()) {
            reason = reason + " (IP: " + ip + ")";
        }
        return truncate(reason);
    }

    private static String truncate(@Nonnull String value) {
        String trimmed = value.trim();
        if (trimmed.length() <= REASON_MAX_LENGTH) {
            return trimmed;
        }
        if (REASON_MAX_LENGTH <= 3) {
            return trimmed.substring(0, REASON_MAX_LENGTH);
        }
        return trimmed.substring(0, REASON_MAX_LENGTH - 3) + "...";
    }

    private static final class BanEntry {
        private final String action;
        private final String target;
        private final String displayName;
        private final String reason;

        private BanEntry(@Nonnull String action,
                         @Nonnull String target,
                         @Nonnull String displayName,
                         @Nonnull String reason) {
            this.action = action;
            this.target = target;
            this.displayName = displayName;
            this.reason = reason;
        }

        private static BanEntry player(@Nonnull UUID uuid, @Nonnull String displayName, @Nonnull String reason) {
            return new BanEntry("Unban", uuid.toString(), displayName, reason);
        }

        private static BanEntry ip(@Nonnull String ip, @Nonnull String displayName, @Nonnull String reason) {
            return new BanEntry("UnipBan", ip, displayName, reason);
        }
    }

    public static final class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec
                .builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Target", Codec.STRING), (d, v) -> d.target = v, d -> d.target).add()
                .build();

        private String action;
        private String target;
    }
}
