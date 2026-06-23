package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionListingModel;
import xyz.thelegacyvoyage.hyessentialsx.models.BanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.MuteModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.economy.EcoAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PluginInfoUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.VanillaBanUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

public final class HyEssentialsXDashboardUI extends InteractiveCustomUIPage<HyEssentialsXDashboardUI.UIEventData> {

    public static final String LAYOUT = "hyessentialsx/Dashboard.ui";
    private static final String PLAYER_ROW_LAYOUT = "hyessentialsx/DashboardPlayerRow.ui";
    private static final int PLAYER_PAGE_SIZE = 9;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final PlayerRef playerRef;
    private final AdminCommandCenterContext context;

    private Tab currentTab = Tab.OVERVIEW;
    private String playerSearch = "";
    private int playerPage;
    private String banReasonInput = "";
    @Nullable
    private UUID selectedPlayerId;
    @Nullable
    private String selectedPlayerName;
    private String statusMessage = "Ready.";
    private String statusColor = "#8fb6e8";

    public HyEssentialsXDashboardUI(@Nonnull PlayerRef playerRef,
                                    @Nonnull AdminCommandCenterContext context) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.context = context;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        bindEvents(evt);
        rebuild(cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {
        if (data.tab != null) {
            currentTab = parseTab(data.tab);
            refresh();
            return;
        }
        if (data.playerSearch != null) {
            String normalized = data.playerSearch.trim().toLowerCase(Locale.ROOT);
            if (!playerSearch.equals(normalized)) {
                playerPage = 0;
            }
            playerSearch = normalized;
            refresh();
            return;
        }
        if ("SelectPlayer".equals(data.playerAction) && data.playerUuid != null) {
            UUID parsed = parseUuid(data.playerUuid);
            if (parsed != null) {
                selectedPlayerId = parsed;
                selectedPlayerName = data.playerName == null || data.playerName.isBlank()
                        ? resolveDisplayName(parsed)
                        : data.playerName;
                status("Selected " + selectedPlayerName + ".", "#8fb6e8");
            }
            refresh();
            return;
        }
        if (data.banReason != null) {
            banReasonInput = data.banReason;
            return;
        }
        if (data.action == null) {
            return;
        }
        handleAction(ref, store, data.action);
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        bindEvents(evt);
        rebuild(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private void bindEvents(@Nonnull UIEventBuilder evt) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", "Close"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton",
                EventData.of("Action", "Refresh"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabOverview",
                EventData.of("Tab", "Overview"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabPlayers",
                EventData.of("Tab", "Players"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabModeration",
                EventData.of("Tab", "Moderation"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabSystems",
                EventData.of("Tab", "Systems"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabLaunch",
                EventData.of("Tab", "Launch"), false);

        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlayerSearch",
                EventData.of("@PlayerSearch", "#PlayerSearch.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PlayerPrevButton",
                EventData.of("Action", "PlayerPrev"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PlayerNextButton",
                EventData.of("Action", "PlayerNext"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FreezeButton",
                EventData.of("Action", "Freeze"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnfreezeButton",
                EventData.of("Action", "Unfreeze"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#MuteButton",
                EventData.of("Action", "Mute"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnmuteButton",
                EventData.of("Action", "Unmute"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnbanButton",
                EventData.of("Action", "Unban"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BanButton",
                EventData.of("Action", "Ban"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BanReasonInput",
                EventData.of("@BanReason", "#BanReasonInput.Value"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenEconomyButton",
                EventData.of("Action", "OpenEconomy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenPlaytimeButton",
                EventData.of("Action", "OpenPlaytime"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenBansButton",
                EventData.of("Action", "OpenBans"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenBansModerationButton",
                EventData.of("Action", "OpenBans"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenShopsButton",
                EventData.of("Action", "OpenShops"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenAuctionButton",
                EventData.of("Action", "OpenAuction"), false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        cmd.set("#VersionText.Text", "v" + PluginInfoUtil.getVersion());
        cmd.set("#StatusText.Text", statusMessage);
        cmd.set("#StatusText.Style.TextColor", statusColor);

        cmd.set("#TabOverview.Text", currentTab == Tab.OVERVIEW ? "OVERVIEW *" : "OVERVIEW");
        cmd.set("#TabPlayers.Text", currentTab == Tab.PLAYERS ? "PLAYERS *" : "PLAYERS");
        cmd.set("#TabModeration.Text", currentTab == Tab.MODERATION ? "MODERATION *" : "MODERATION");
        cmd.set("#TabSystems.Text", currentTab == Tab.SYSTEMS ? "SYSTEMS *" : "SYSTEMS");
        cmd.set("#TabLaunch.Text", currentTab == Tab.LAUNCH ? "LAUNCH *" : "LAUNCH");

        cmd.set("#OverviewContent.Visible", currentTab == Tab.OVERVIEW);
        cmd.set("#PlayersContent.Visible", currentTab == Tab.PLAYERS);
        cmd.set("#ModerationContent.Visible", currentTab == Tab.MODERATION);
        cmd.set("#SystemsContent.Visible", currentTab == Tab.SYSTEMS);
        cmd.set("#LaunchContent.Visible", currentTab == Tab.LAUNCH);

        buildOverview(cmd);
        buildPlayers(cmd, evt);
        buildModeration(cmd);
        buildSystems(cmd);
        buildLaunch(cmd);
    }

    private void buildOverview(@Nonnull UICommandBuilder cmd) {
        Snapshot snapshot = snapshot();
        cmd.set("#OnlineValue.Text", String.valueOf(snapshot.onlinePlayers));
        cmd.set("#TrackedValue.Text", String.valueOf(snapshot.trackedPlayers));
        cmd.set("#ModerationValue.Text", String.valueOf(snapshot.openModerationItems));
        cmd.set("#MarketValue.Text", String.valueOf(snapshot.marketItems));

        cmd.clear("#InsightList");
        appendInsight(cmd, "Players", snapshot.onlinePlayers + " online, " + snapshot.trackedPlayers + " tracked.");
        appendInsight(cmd, "Moderation", snapshot.bans + " bans, " + snapshot.mutes + " mutes, " + snapshot.frozen + " frozen.");
        appendInsight(cmd, "Economy", snapshot.economySummary);
        appendInsight(cmd, "Markets", snapshot.adminShops + " admin shops, " + snapshot.playerShops
                + " player shops, " + snapshot.auctions + " auction listings.");
        appendInsight(cmd, "Travel", snapshot.warps + " warps and " + snapshot.homes + " player homes indexed.");
        appendInsight(cmd, "Storage", context.config().getStorageType().toUpperCase(Locale.ROOT)
                + " backend, " + context.kitManager().listKits().size() + " kits.");
    }

    private void buildPlayers(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        cmd.set("#PlayerSearch.Value", playerSearch);
        List<PlayerRow> rows = collectPlayers(playerSearch);
        int pages = Math.max(1, (int) Math.ceil(rows.size() / (double) PLAYER_PAGE_SIZE));
        if (playerPage >= pages) {
            playerPage = pages - 1;
        }
        int start = playerPage * PLAYER_PAGE_SIZE;
        int end = Math.min(rows.size(), start + PLAYER_PAGE_SIZE);

        cmd.clear("#PlayerList");
        for (int i = start; i < end; i++) {
            PlayerRow row = rows.get(i);
            int displayIndex = i - start;
            cmd.append("#PlayerList", PLAYER_ROW_LAYOUT);
            String base = "#PlayerList[" + displayIndex + "]";
            cmd.set(base + " #PlayerName.Text", row.name);
            cmd.set(base + " #PlayerState.Text", row.status);
            cmd.set(base + " #PlayerBalance.Text", context.economyManager() == null ? "-" : context.economyManager().formatAmount(row.balance));
            cmd.set(base + " #PlayerPlaytime.Text", TimeUtil.formatDurationSeconds(row.playtimeSeconds));
            cmd.set(base + " #OnlineIndicator.Visible", row.online);
            evt.addEventBinding(CustomUIEventBindingType.Activating, base + " #SelectPlayerButton",
                    EventData.of("PlayerAction", "SelectPlayer")
                            .append("PlayerUuid", row.uuid.toString())
                            .append("PlayerName", row.name), false);
        }
        if (start >= end) {
            cmd.appendInline("#PlayerList", "Label { Text: \"No matching players.\"; Anchor: (Height: 36); "
                    + "Style: (FontSize: 12, TextColor: #8193aa, HorizontalAlignment: Center, VerticalAlignment: Center); }");
        }

        cmd.set("#PlayerPageInfo.Text", "Page " + (playerPage + 1) + "/" + pages + "  |  " + rows.size() + " result(s)");
        buildSelectedPlayer(cmd);
    }

    private void buildSelectedPlayer(@Nonnull UICommandBuilder cmd) {
        UUID selected = selectedPlayerId;
        if (selected == null) {
            cmd.set("#SelectedName.Text", "No player selected");
            cmd.set("#SelectedMeta.Text", "Pick a player from the list to inspect state and use quick actions.");
            cmd.set("#SelectedState.Text", "");
            cmd.set("#SelectedCommandHint.Text", "Command hints appear here after selecting a player.");
            return;
        }
        cmd.set("#BanReasonInput.Value", banReasonInput);
        PlayerDataModel data = context.storage().getPlayerData(selected);
        String name = selectedPlayerName == null || selectedPlayerName.isBlank() ? resolveDisplayName(selected) : selectedPlayerName;
        String online = Universe.get().getPlayer(selected) != null ? "Online" : "Offline";
        String lastSeen = data.getLastSeenAt() <= 0L ? "never" : formatDate(data.getLastSeenAt());
        cmd.set("#SelectedName.Text", name);
        cmd.set("#SelectedMeta.Text", online + " | UUID: " + selected + " | Last seen: " + lastSeen);
        cmd.set("#SelectedState.Text", playerStateLine(selected, data));
        cmd.set("#SelectedCommandHint.Text", "/whois " + name + "   /seen " + name + "   /iphistory " + name
                + "   /tempban " + name + " <time> <reason>");
    }

    private void buildModeration(@Nonnull UICommandBuilder cmd) {
        Snapshot snapshot = snapshot();
        cmd.set("#BanCountValue.Text", String.valueOf(snapshot.bans));
        cmd.set("#MuteCountValue.Text", String.valueOf(snapshot.mutes));
        cmd.set("#FrozenCountValue.Text", String.valueOf(snapshot.frozen));
        cmd.set("#IpBanCountValue.Text", String.valueOf(snapshot.ipBans));

        cmd.clear("#ModerationQueue");
        List<PlayerRow> rows = collectPlayers("");
        int added = 0;
        for (PlayerRow row : rows) {
            if (!row.banned && !row.muted && !row.frozen) {
                continue;
            }
            cmd.appendInline("#ModerationQueue", "Label { Text: \"" + escapeInline(row.name + " - " + row.status)
                    + "\"; Anchor: (Height: 24, Bottom: 3); Style: (FontSize: 11, TextColor: #d9e7f7); }");
            added++;
            if (added >= 14) {
                break;
            }
        }
        if (added == 0) {
            cmd.appendInline("#ModerationQueue", "Label { Text: \"No active player moderation records.\"; "
                    + "Anchor: (Height: 28); Style: (FontSize: 12, TextColor: #8193aa); }");
        }
    }

    private void buildSystems(@Nonnull UICommandBuilder cmd) {
        Snapshot snapshot = snapshot();
        cmd.set("#StorageValue.Text", context.config().getStorageType().toUpperCase(Locale.ROOT));
        cmd.set("#EconomyValue.Text", snapshot.economySummary);
        cmd.set("#ShopValue.Text", snapshot.adminShops + " admin / " + snapshot.playerShops + " player");
        cmd.set("#AuctionValue.Text", snapshot.auctions + " active listings");
        cmd.set("#TravelValue.Text", snapshot.warps + " warps / " + snapshot.homes + " homes");
        cmd.set("#ProgressionValue.Text", TimeUtil.formatDurationSeconds(snapshot.totalPlaytimeSeconds)
                + " total playtime / " + context.config().getPlaytimeRewards().size() + " rewards");
    }

    private void buildLaunch(@Nonnull UICommandBuilder cmd) {
        cmd.set("#EconomyLaunchState.Text", canUse("hyessentialsx.ecoadmin")
                ? "Available: balances, top list, audit log, HUD config."
                : "Missing permission: hyessentialsx.ecoadmin");
        cmd.set("#PlaytimeLaunchState.Text", canUse("hyessentialsx.playtime.admin")
                ? "Available: rewards, top playtime, rankup config."
                : "Missing permission: hyessentialsx.playtime.admin");
        cmd.set("#BanLaunchState.Text", canUse("hyessentialsx.banlist")
                ? "Available: ban and IP-ban review."
                : "Missing permission: hyessentialsx.banlist");
        cmd.set("#ShopLaunchState.Text", canUse("hyessentialsx.playershop.admin") || canUse("hyessentialsx.adminshop.admin")
                ? "Available: shop directory, player shops, admin shops."
                : "Needs shop admin permissions for full control.");
        cmd.set("#AuctionLaunchState.Text", canUse("hyessentialsx.auctionhouse.admin") || canUse("hyessentialsx.auctionhouse.use")
                ? "Available: auction browser and listing tools."
                : "Missing auction house permission.");
    }

    private void handleAction(@Nonnull Ref<EntityStore> ref,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull String action) {
        switch (action) {
            case "Close" -> close();
            case "Refresh" -> {
                status("Command center refreshed.", "#55d98b");
                refresh();
                notify("Command center refreshed.", NotificationStyle.Success);
            }
            case "PlayerPrev" -> {
                if (playerPage > 0) {
                    playerPage--;
                }
                refresh();
            }
            case "PlayerNext" -> {
                playerPage++;
                refresh();
            }
            case "Freeze" -> {
                freezeSelected();
                refresh();
            }
            case "Unfreeze" -> {
                unfreezeSelected();
                refresh();
            }
            case "Mute" -> {
                muteSelected();
                refresh();
            }
            case "Unmute" -> {
                unmuteSelected();
                refresh();
            }
            case "Unban" -> {
                unbanSelected();
                refresh();
            }
            case "Ban" -> {
                banSelected();
                refresh();
            }
            case "OpenEconomy" -> openEconomy(ref, store);
            case "OpenPlaytime" -> openPlaytime(ref, store);
            case "OpenBans" -> openBans(ref, store);
            case "OpenShops" -> openShops(ref, store);
            case "OpenAuction" -> openAuction(ref, store);
            default -> {
            }
        }
    }

    private void freezeSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.freeze")) {
            status("Missing permission: hyessentialsx.freeze", "#ff7d7d");
            return;
        }
        PlayerRef target = Universe.get().getPlayer(id);
        if (target == null) {
            status("Freeze requires the target to be online.", "#ffb86b");
            return;
        }
        if (context.freezeManager().isFrozenOrStored(id)) {
            status(resolveDisplayName(id) + " is already frozen.", "#ffb86b");
            return;
        }
        context.freezeManager().freeze(target);
        Messages.sendPrefixedKey(target, "freeze.target", Map.of());
        status("Frozen " + target.getUsername() + ".", "#55d98b");
        notify(target.getUsername() + " frozen.", NotificationStyle.Success);
    }

    private void unfreezeSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.unfreeze")) {
            status("Missing permission: hyessentialsx.unfreeze", "#ff7d7d");
            return;
        }
        context.freezeManager().unfreeze(id);
        PlayerRef target = Universe.get().getPlayer(id);
        if (target != null) {
            Messages.sendPrefixedKey(target, "freeze.unfrozen_target", Map.of());
        }
        status("Unfroze " + resolveDisplayName(id) + ".", "#55d98b");
    }

    private void muteSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.mute")) {
            status("Missing permission: hyessentialsx.mute", "#ff7d7d");
            return;
        }
        String name = resolveDisplayName(id);
        MuteModel mute = new MuteModel(name, playerRef.getUsername(), "Muted from admin command center.", 0L, System.currentTimeMillis());
        context.muteManager().mute(id, mute);
        status("Muted " + name + ".", "#55d98b");
    }

    private void unmuteSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.unmute")) {
            status("Missing permission: hyessentialsx.unmute", "#ff7d7d");
            return;
        }
        context.muteManager().unmute(id);
        status("Unmuted " + resolveDisplayName(id) + ".", "#55d98b");
    }

    private void unbanSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.unban")) {
            status("Missing permission: hyessentialsx.unban", "#ff7d7d");
            return;
        }
        context.banManager().unban(id);
        VanillaBanUtil.unbanVanilla(id);
        status("Unbanned " + resolveDisplayName(id) + ".", "#55d98b");
    }

    private void banSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.ban")) {
            status("Missing permission: hyessentialsx.ban", "#ff7d7d");
            return;
        }
        if (id.equals(playerRef.getUuid())) {
            status("You cannot ban yourself from the command center.", "#ffb86b");
            return;
        }
        if (context.banManager().isBanned(id)) {
            status(resolveDisplayName(id) + " is already banned.", "#ffb86b");
            return;
        }

        String name = resolveDisplayName(id);
        String reason = banReasonInput == null ? "" : banReasonInput.trim();
        if (reason.isBlank()) {
            reason = Messages.tr(playerRef, "reason.none", Map.of());
        }

        context.banManager().ban(id, new BanModel(
                name,
                playerRef.getUsername(),
                reason,
                0L,
                System.currentTimeMillis()
        ));

        PlayerRef target = Universe.get().getPlayer(id);
        if (target != null) {
            String timeText = Messages.tr(target, "ban.permanent", Map.of());
            target.getPacketHandler().disconnect(Messages.m(Messages.tr(target, "ban.kick", Map.of(
                    "reason", reason,
                    "time", timeText
            ))));
        }

        banReasonInput = "";
        status("Banned " + name + ".", "#55d98b");
    }

    private void openEconomy(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!canUse("hyessentialsx.ecoadmin")) {
            status("Missing permission: hyessentialsx.ecoadmin", "#ff7d7d");
            refresh();
            return;
        }
        if (context.economyManager() == null || context.economyHudManager() == null || context.economyAuditManager() == null) {
            status("Economy admin is unavailable.", "#ffb86b");
            refresh();
            return;
        }
        Player player = player(ref, store);
        if (player != null) {
            new EcoAdminUI(playerRef, context.economyManager(), context.storage(), context.config(),
                    context.economyHudManager(), context.economyAuditManager(), commandCenterBackTarget()).open(player, ref, store);
        }
    }

    private void openPlaytime(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!canUse("hyessentialsx.playtime.admin")) {
            status("Missing permission: hyessentialsx.playtime.admin", "#ff7d7d");
            refresh();
            return;
        }
        Player player = player(ref, store);
        if (player != null) {
            new PlaytimeAdminUI(playerRef, context.playtimeManager(), context.playtimeRewardManager(),
                    context.rankupManager(), context.storage(), context.config(), commandCenterBackTarget()).open(player, ref, store);
        }
    }

    private void openBans(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!canUse("hyessentialsx.banlist")) {
            status("Missing permission: hyessentialsx.banlist", "#ff7d7d");
            refresh();
            return;
        }
        Player player = player(ref, store);
        if (player != null) {
            new BanListUI(playerRef, context.banManager(), context.ipBanManager(), context.storage(), commandCenterBackTarget()).open(player, ref, store);
        }
    }

    private void openShops(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (context.shopManager() == null || context.economyManager() == null || context.shopAdminDraftCache() == null
                || context.auctionHouseManager() == null) {
            status("Shop directory is unavailable.", "#ffb86b");
            refresh();
            return;
        }
        Player player = player(ref, store);
        if (player != null) {
            new ShopDirectoryUI(playerRef, context.shopManager(), context.economyManager(),
                    context.shopAdminDraftCache(), context.config(), context.storage(),
                    context.auctionHouseManager(), commandCenterBackTarget()).open(player, ref, store);
        }
    }

    private void openAuction(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (context.auctionHouseManager() == null || context.economyManager() == null) {
            status("Auction house is unavailable.", "#ffb86b");
            refresh();
            return;
        }
        if (!canUse("hyessentialsx.auctionhouse.use") && !canUse("hyessentialsx.auctionhouse.admin")) {
            status("Missing auction house permission.", "#ff7d7d");
            refresh();
            return;
        }
        Player player = player(ref, store);
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store,
                    new AuctionHouseUI(playerRef, context.auctionHouseManager(), context.economyManager(), context.config(),
                            commandCenterBackTarget()));
        }
    }

    @Nonnull
    private UiBackTarget commandCenterBackTarget() {
        Tab returnTab = currentTab;
        String returnSearch = playerSearch;
        int returnPage = playerPage;
        UUID returnSelectedId = selectedPlayerId;
        String returnSelectedName = selectedPlayerName;
        String returnBanReason = banReasonInput;
        return (player, ref, store) -> {
            HyEssentialsXDashboardUI page = new HyEssentialsXDashboardUI(playerRef, context);
            page.currentTab = returnTab;
            page.playerSearch = returnSearch;
            page.playerPage = returnPage;
            page.selectedPlayerId = returnSelectedId;
            page.selectedPlayerName = returnSelectedName;
            page.banReasonInput = returnBanReason;
            page.open(player, ref, store);
        };
    }

    @Nullable
    private Player player(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        return store.getComponent(ref, Player.getComponentType());
    }

    @Nullable
    private UUID requireSelected() {
        if (selectedPlayerId == null) {
            status("Select a player first.", "#ffb86b");
            return null;
        }
        return selectedPlayerId;
    }

    @Nonnull
    private List<PlayerRow> collectPlayers(@Nonnull String search) {
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        Set<UUID> ids = new HashSet<>(context.storage().listPlayerIds());
        for (PlayerRef online : Universe.get().getPlayers()) {
            if (online != null) {
                ids.add(online.getUuid());
            }
        }

        List<PlayerRow> rows = new ArrayList<>();
        for (UUID id : ids) {
            String name = resolveDisplayName(id);
            if (!normalized.isBlank() && !name.toLowerCase(Locale.ROOT).contains(normalized)
                    && !id.toString().contains(normalized)) {
                continue;
            }
            PlayerDataModel data = context.storage().getPlayerData(id);
            boolean online = Universe.get().getPlayer(id) != null;
            boolean banned = context.banManager().isBanned(id);
            boolean muted = context.muteManager().isMuted(id);
            boolean frozen = context.freezeManager().isFrozenOrStored(id);
            long balance = context.economyManager() == null ? 0L : context.economyManager().getBalance(id);
            String status = stateSummary(online, banned, muted, frozen);
            rows.add(new PlayerRow(id, name, online, banned, muted, frozen, balance, data.getPlaytimeSeconds(), status));
        }
        rows.sort(Comparator.comparing((PlayerRow row) -> !row.online)
                .thenComparing(row -> row.name.toLowerCase(Locale.ROOT)));
        return rows;
    }

    @Nonnull
    private String playerStateLine(@Nonnull UUID id, @Nonnull PlayerDataModel data) {
        String balance = context.economyManager() == null ? "Economy disabled" : context.economyManager().formatAmount(context.economyManager().getBalance(id));
        int mail = data.getMailInbox().size();
        int homes = data.getHomes().size();
        return stateSummary(Universe.get().getPlayer(id) != null,
                context.banManager().isBanned(id),
                context.muteManager().isMuted(id),
                context.freezeManager().isFrozenOrStored(id))
                + " | Balance: " + balance
                + " | Playtime: " + TimeUtil.formatDurationSeconds(data.getPlaytimeSeconds())
                + " | Homes: " + homes
                + " | Inbox: " + mail;
    }

    @Nonnull
    private String stateSummary(boolean online, boolean banned, boolean muted, boolean frozen) {
        List<String> states = new ArrayList<>();
        states.add(online ? "Online" : "Offline");
        if (banned) states.add("Banned");
        if (muted) states.add("Muted");
        if (frozen) states.add("Frozen");
        return String.join(" / ", states);
    }

    private Snapshot snapshot() {
        Set<UUID> ids = new HashSet<>(context.storage().listPlayerIds());
        List<PlayerRef> onlinePlayers = new ArrayList<>();
        for (PlayerRef online : Universe.get().getPlayers()) {
            if (online != null) {
                onlinePlayers.add(online);
                ids.add(online.getUuid());
            }
        }

        int bans = 0;
        int mutes = 0;
        int frozen = 0;
        int homes = 0;
        long totalPlaytime = 0L;
        long totalEconomy = 0L;
        for (UUID id : ids) {
            PlayerDataModel data = context.storage().getPlayerData(id);
            if (context.banManager().isBanned(id)) bans++;
            if (context.muteManager().isMuted(id)) mutes++;
            if (context.freezeManager().isFrozenOrStored(id)) frozen++;
            homes += data.getHomes().size();
            totalPlaytime = saturatedAdd(totalPlaytime, data.getPlaytimeSeconds());
            if (context.economyManager() != null) {
                totalEconomy = saturatedAdd(totalEconomy, context.economyManager().getBalance(id));
            }
        }

        int adminShops = 0;
        int playerShops = 0;
        if (context.shopManager() != null) {
            for (ShopModel shop : context.storage().getShops().values()) {
                if (shop == null) continue;
                if (shop.isPlayerShop()) playerShops++;
                else adminShops++;
            }
        }

        int auctions = 0;
        if (context.auctionHouseManager() != null) {
            List<AuctionListingModel> active = context.auctionHouseManager().listActive();
            auctions = active.size();
        }

        String economy = context.economyManager() == null
                ? "Disabled"
                : context.economyManager().formatAmount(totalEconomy) + " circulating";

        return new Snapshot(
                onlinePlayers.size(),
                ids.size(),
                bans,
                mutes,
                frozen,
                context.storage().getIpBans().size(),
                bans + mutes + frozen + context.storage().getIpBans().size(),
                adminShops,
                playerShops,
                auctions,
                adminShops + playerShops + auctions,
                context.storage().getWarps().size(),
                homes,
                totalPlaytime,
                economy
        );
    }

    private void appendInsight(@Nonnull UICommandBuilder cmd, @Nonnull String label, @Nonnull String value) {
        cmd.appendInline("#InsightList", "Group { LayoutMode: Left; Anchor: (Height: 28, Bottom: 4); "
                + "Label { Text: \"" + escapeInline(label) + "\"; Anchor: (Width: 110); "
                + "Style: (FontSize: 11, TextColor: #8fb6e8, RenderBold: true); } "
                + "Label { Text: \"" + escapeInline(value) + "\"; FlexWeight: 1; "
                + "Style: (FontSize: 11, TextColor: #d7e7ff, ShrinkTextToFit: true, MinShrinkTextToFitFontSize: 8); } "
                + "}");
    }

    @Nonnull
    private String resolveDisplayName(@Nonnull UUID id) {
        PlayerRef online = Universe.get().getPlayer(id);
        if (online != null && online.getUsername() != null && !online.getUsername().isBlank()) {
            return online.getUsername();
        }
        BanModel ban = context.banManager().getBan(id);
        if (ban != null && ban.getPlayerName() != null && !ban.getPlayerName().isBlank()) {
            return ban.getPlayerName();
        }
        MuteModel mute = context.muteManager().getMute(id);
        if (mute != null && mute.getPlayerName() != null && !mute.getPlayerName().isBlank()) {
            return mute.getPlayerName();
        }
        PlayerDataModel data = context.storage().getPlayerData(id);
        String stored = data.getLastKnownName();
        return stored == null || stored.isBlank() ? id.toString() : stored;
    }

    private boolean canUse(@Nonnull String permission) {
        return CommandPermissionUtil.hasPermission(playerRef, permission);
    }

    private void notify(@Nonnull String text, @Nonnull NotificationStyle style) {
        NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.raw("HyEssentialsX"),
                Message.raw(text),
                style
        );
    }

    private void status(@Nonnull String message, @Nonnull String color) {
        statusMessage = message;
        statusColor = color;
    }

    @Nonnull
    private String formatDate(long epochMs) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(Math.max(0L, epochMs)).atZone(ZoneId.systemDefault()));
    }

    @Nullable
    private UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private long saturatedAdd(long a, long b) {
        try {
            return Math.addExact(a, Math.max(0L, b));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    @Nonnull
    private String escapeInline(@Nonnull String text) {
        return text.replace("\\", "\\\\").replace("\"", "'");
    }

    @Nonnull
    private Tab parseTab(@Nonnull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "players" -> Tab.PLAYERS;
            case "moderation" -> Tab.MODERATION;
            case "systems" -> Tab.SYSTEMS;
            case "launch" -> Tab.LAUNCH;
            default -> Tab.OVERVIEW;
        };
    }

    private enum Tab {
        OVERVIEW,
        PLAYERS,
        MODERATION,
        SYSTEMS,
        LAUNCH
    }

    private record PlayerRow(UUID uuid,
                             String name,
                             boolean online,
                             boolean banned,
                             boolean muted,
                             boolean frozen,
                             long balance,
                             long playtimeSeconds,
                             String status) {
    }

    private record Snapshot(int onlinePlayers,
                            int trackedPlayers,
                            int bans,
                            int mutes,
                            int frozen,
                            int ipBans,
                            int openModerationItems,
                            int adminShops,
                            int playerShops,
                            int auctions,
                            int marketItems,
                            int warps,
                            int homes,
                            long totalPlaytimeSeconds,
                            String economySummary) {
    }

    public static final class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec
                .builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Tab", Codec.STRING), (d, v) -> d.tab = v, d -> d.tab).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("PlayerAction", Codec.STRING), (d, v) -> d.playerAction = v, d -> d.playerAction).add()
                .append(new KeyedCodec<>("PlayerUuid", Codec.STRING), (d, v) -> d.playerUuid = v, d -> d.playerUuid).add()
                .append(new KeyedCodec<>("PlayerName", Codec.STRING), (d, v) -> d.playerName = v, d -> d.playerName).add()
                .append(new KeyedCodec<>("@PlayerSearch", Codec.STRING), (d, v) -> d.playerSearch = v, d -> d.playerSearch).add()
                .append(new KeyedCodec<>("@BanReason", Codec.STRING), (d, v) -> d.banReason = v, d -> d.banReason).add()
                .build();

        private String tab;
        private String action;
        private String playerAction;
        private String playerUuid;
        private String playerName;
        private String playerSearch;
        private String banReason;
    }
}
