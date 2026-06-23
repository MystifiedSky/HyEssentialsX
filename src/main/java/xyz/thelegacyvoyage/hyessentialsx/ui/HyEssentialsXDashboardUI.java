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
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.IpHistoryModel;
import xyz.thelegacyvoyage.hyessentialsx.models.MuteModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffActivityEntryModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffCaseModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffNoteModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.economy.EcoAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PluginInfoUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.StaffActionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class HyEssentialsXDashboardUI extends InteractiveCustomUIPage<HyEssentialsXDashboardUI.UIEventData> {

    public static final String LAYOUT = "hyessentialsx/Dashboard.ui";
    private static final String PLAYER_ROW_LAYOUT = "hyessentialsx/DashboardPlayerRow.ui";
    private static final int PLAYER_PAGE_SIZE = 13;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final PlayerRef playerRef;
    private final AdminCommandCenterContext context;

    private Tab currentTab = Tab.OVERVIEW;
    private ProfileTab profileTab = ProfileTab.OVERVIEW;
    private PlayerFilter playerFilter = PlayerFilter.ALL;
    private PlayerSort playerSort = PlayerSort.NAME;
    private String playerSearch = "";
    private int playerPage;
    private String actionReasonInput = "";
    private boolean actionsOpen = true;
    @Nullable
    private String pendingAction;
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
        if (data.profileTab != null) {
            profileTab = parseProfileTab(data.profileTab);
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
        if (data.actionReason != null) {
            actionReasonInput = data.actionReason;
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
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterAllButton",
                EventData.of("Action", "FilterAll"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterOnlineButton",
                EventData.of("Action", "FilterOnline"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterPunishedButton",
                EventData.of("Action", "FilterPunished"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterWarnedButton",
                EventData.of("Action", "FilterWarned"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterBalanceButton",
                EventData.of("Action", "FilterBalance"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterNewButton",
                EventData.of("Action", "FilterNew"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SortButton",
                EventData.of("Action", "CycleSort"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileTabOverview",
                EventData.of("ProfileTab", "Overview"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileTabPunishments",
                EventData.of("ProfileTab", "Punishments"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileTabEconomy",
                EventData.of("ProfileTab", "Economy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileTabTravel",
                EventData.of("ProfileTab", "Travel"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileTabStats",
                EventData.of("ProfileTab", "Stats"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ProfileTabActivity",
                EventData.of("ProfileTab", "Activity"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleActionsButton",
                EventData.of("Action", "ToggleActions"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmActionButton",
                EventData.of("Action", "ConfirmPending"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CancelActionButton",
                EventData.of("Action", "CancelPending"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ReasonSpamButton",
                EventData.of("Action", "ReasonSpam"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ReasonHarassmentButton",
                EventData.of("Action", "ReasonHarassment"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ReasonExploitingButton",
                EventData.of("Action", "ReasonExploiting"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ReasonChargebackButton",
                EventData.of("Action", "ReasonChargeback"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ReasonAltButton",
                EventData.of("Action", "ReasonAlt"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FreezeButton",
                EventData.of("Action", "Freeze"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnfreezeButton",
                EventData.of("Action", "Unfreeze"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#MuteButton",
                EventData.of("Action", "Mute"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnmuteButton",
                EventData.of("Action", "Unmute"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#WarnButton",
                EventData.of("Action", "Warn"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#AddNoteButton",
                EventData.of("Action", "AddNote"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearWarningsButton",
                EventData.of("Action", "ClearWarnings"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnbanButton",
                EventData.of("Action", "Unban"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BanButton",
                EventData.of("Action", "Ban"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportToButton",
                EventData.of("Action", "TeleportTo"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportHereButton",
                EventData.of("Action", "TeleportHere"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ResetBalanceButton",
                EventData.of("Action", "ResetBalance"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BanReasonInput",
                EventData.of("@ActionReason", "#BanReasonInput.Value"), false);

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
        cmd.set("#FilterAllButton.Text", playerFilter == PlayerFilter.ALL ? "All *" : "All");
        cmd.set("#FilterOnlineButton.Text", playerFilter == PlayerFilter.ONLINE ? "Online *" : "Online");
        cmd.set("#FilterPunishedButton.Text", playerFilter == PlayerFilter.PUNISHED ? "Punished *" : "Punished");
        cmd.set("#FilterWarnedButton.Text", playerFilter == PlayerFilter.WARNED ? "Warned *" : "Warned");
        cmd.set("#FilterBalanceButton.Text", playerFilter == PlayerFilter.HAS_BALANCE ? "Balance *" : "Balance");
        cmd.set("#FilterNewButton.Text", playerFilter == PlayerFilter.NEW ? "New *" : "New");
        cmd.set("#SortButton.Text", "Sort: " + playerSort.label());
        buildSelectedPlayer(cmd);
    }

    private void buildSelectedPlayer(@Nonnull UICommandBuilder cmd) {
        buildSelectedPanelChrome(cmd);
        UUID selected = selectedPlayerId;
        if (selected == null) {
            cmd.set("#SelectedName.Text", "No player selected");
            cmd.set("#SelectedMeta.Text", "Pick a player from the list to inspect state and use quick actions.");
            cmd.clear("#RiskFlagList");
            cmd.appendInline("#RiskFlagList", inlineLabel("No player selected.", "#8193aa", 24));
            cmd.set("#SelectedModuleTitle.Text", "Selected Player");
            cmd.clear("#SelectedModuleList");
            cmd.appendInline("#SelectedModuleList", inlineLabel("Select a player to open the staff profile workspace.", "#8193aa", 32));
            return;
        }
        cmd.set("#BanReasonInput.Value", actionReasonInput);
        PlayerDataModel data = context.storage().getPlayerData(selected);
        String name = selectedPlayerName == null || selectedPlayerName.isBlank() ? resolveDisplayName(selected) : selectedPlayerName;
        String online = Universe.get().getPlayer(selected) != null ? "Online" : "Offline";
        String lastSeen = data.getLastSeenAt() <= 0L ? "never" : formatDate(data.getLastSeenAt());
        cmd.set("#SelectedName.Text", name);
        cmd.set("#SelectedMeta.Text", online + " | UUID: " + selected + " | Last seen: " + lastSeen);
        buildRiskFlags(cmd, selected, data);
        buildSelectedModule(cmd, selected, data, name);
    }

    private void buildSelectedPanelChrome(@Nonnull UICommandBuilder cmd) {
        cmd.set("#ProfileTabOverview.Text", profileTab == ProfileTab.OVERVIEW ? "Overview *" : "Overview");
        cmd.set("#ProfileTabPunishments.Text", profileTab == ProfileTab.PUNISHMENTS ? "Punish *" : "Punish");
        cmd.set("#ProfileTabEconomy.Text", profileTab == ProfileTab.ECONOMY ? "Economy *" : "Economy");
        cmd.set("#ProfileTabTravel.Text", profileTab == ProfileTab.TRAVEL ? "Travel *" : "Travel");
        cmd.set("#ProfileTabStats.Text", profileTab == ProfileTab.STATS ? "Stats *" : "Stats");
        cmd.set("#ProfileTabActivity.Text", profileTab == ProfileTab.ACTIVITY ? "Activity *" : "Activity");
        cmd.set("#ActionDrawer.Visible", actionsOpen);
        cmd.set("#ToggleActionsButton.Text", actionsOpen ? "Hide Actions" : "Show Actions");
        cmd.set("#PendingConfirmPanel.Visible", pendingAction != null);
        cmd.set("#PendingConfirmText.Text", pendingAction == null ? "" : pendingText(pendingAction));
        cmd.set("#DangerActionsPanel.Visible", selectedPlayerId != null
                && (pendingAction != null || profileTab == ProfileTab.PUNISHMENTS || profileTab == ProfileTab.ECONOMY));
        cmd.set("#TravelActionsPanel.Visible", selectedPlayerId != null && profileTab == ProfileTab.TRAVEL);
        configureActionButton(cmd, "#FreezeButton", "Freeze", "hyessentialsx.freeze");
        configureActionButton(cmd, "#UnfreezeButton", "Unfreeze", "hyessentialsx.unfreeze");
        configureActionButton(cmd, "#MuteButton", "Mute", "hyessentialsx.mute");
        configureActionButton(cmd, "#UnmuteButton", "Unmute", "hyessentialsx.unmute");
        configureActionButton(cmd, "#WarnButton", "Warn", "hyessentialsx.warn");
        configureActionButton(cmd, "#ClearWarningsButton", "Clear Warns", "hyessentialsx.clearwarnings");
        configureActionButton(cmd, "#BanButton", "Ban", "hyessentialsx.ban");
        configureActionButton(cmd, "#UnbanButton", "Unban", "hyessentialsx.unban");
        configureActionButton(cmd, "#ResetBalanceButton", "Reset $", "hyessentialsx.ecoadmin");
    }

    private void configureActionButton(@Nonnull UICommandBuilder cmd,
                                       @Nonnull String selector,
                                       @Nonnull String label,
                                       @Nonnull String permission) {
        cmd.set(selector + ".Text", canUse(permission) ? label : "No Permission");
    }

    private void buildRiskFlags(@Nonnull UICommandBuilder cmd, @Nonnull UUID id, @Nonnull PlayerDataModel data) {
        cmd.clear("#RiskFlagList");
        boolean online = Universe.get().getPlayer(id) != null;
        boolean banned = context.banManager().isBanned(id);
        boolean muted = context.muteManager().isMuted(id);
        boolean frozen = context.freezeManager().isFrozenOrStored(id);
        long warnings = context.storage().countActiveWarnings(id);
        long balance = context.economyManager() == null ? 0L : context.economyManager().getBalance(id);
        List<String> flags = new ArrayList<>();
        flags.add(online ? "Online" : "Offline");
        if (banned) flags.add("Banned");
        if (muted) flags.add("Muted");
        if (frozen) flags.add("Frozen");
        if (warnings > 0L) flags.add("Warnings " + warnings);
        if (recentIpChange(data)) flags.add("Recent IP");
        if (isNewPlayer(data)) flags.add("New Player");
        if (context.economyManager() != null && balance >= highBalanceThreshold()) flags.add("High Balance");
        if (possibleAltMatch(id, data)) flags.add("Alt Match");
        if (flags.size() == 1 && !banned && !muted && !frozen && warnings <= 0L) {
            flags.add("Low Risk");
        }
        for (String flag : flags) {
            cmd.appendInline("#RiskFlagList", "Label { Text: \"" + escapeInline(flag)
                    + "\"; Anchor: (Height: 20, Bottom: 3); Style: (FontSize: 10, TextColor: "
                    + riskColor(flag) + ", RenderBold: true, ShrinkTextToFit: true, MinShrinkTextToFitFontSize: 8); }");
        }
    }

    private void buildSelectedModule(@Nonnull UICommandBuilder cmd,
                                     @Nonnull UUID id,
                                     @Nonnull PlayerDataModel data,
                                     @Nonnull String name) {
        cmd.clear("#SelectedModuleList");
        switch (profileTab) {
            case OVERVIEW -> buildOverviewModule(cmd, id, data, name);
            case PUNISHMENTS -> buildPunishmentsModule(cmd, id, data);
            case ECONOMY -> buildEconomyModule(cmd, id);
            case TRAVEL -> buildTravelModule(cmd, data);
            case STATS -> buildStatsModule(cmd, data);
            case ACTIVITY -> buildActivityModule(cmd, id, data);
        }
    }

    private void buildOverviewModule(@Nonnull UICommandBuilder cmd,
                                     @Nonnull UUID id,
                                     @Nonnull PlayerDataModel data,
                                     @Nonnull String name) {
        cmd.set("#SelectedModuleTitle.Text", "Overview");
        appendModuleRow(cmd, "State", playerStateLine(id, data));
        appendModuleRow(cmd, "First Join", data.getFirstJoinAt() <= 0L ? "unknown" : formatDate(data.getFirstJoinAt()));
        appendModuleRow(cmd, "Last Join", data.getLastJoinAt() <= 0L ? "unknown" : formatDate(data.getLastJoinAt()));
        appendModuleRow(cmd, "Playtime", TimeUtil.formatDurationSeconds(data.getPlaytimeSeconds()));
        appendModuleRow(cmd, "Inbox", data.getMailInbox().size() + " mail, " + data.getHomes().size() + " homes");
        appendModuleRow(cmd, "Commands", "/whois " + name + " | /seen " + name + " | /iphistory " + name);
        appendModuleHeader(cmd, "Staff Notes");
        appendNotes(cmd, data, 3);
    }

    private void buildPunishmentsModule(@Nonnull UICommandBuilder cmd, @Nonnull UUID id, @Nonnull PlayerDataModel data) {
        cmd.set("#SelectedModuleTitle.Text", "Punishments");
        appendModuleRow(cmd, "Status", stateSummary(Universe.get().getPlayer(id) != null,
                context.banManager().isBanned(id),
                context.muteManager().isMuted(id),
                context.freezeManager().isFrozenOrStored(id)));
        appendModuleHeader(cmd, "Warnings");
        if (!canUse("hyessentialsx.warnings")
                && !canUse("hyessentialsx.ui.playerdetails")
                && !canUse("hyessentialsx.whois")) {
            cmd.appendInline("#SelectedModuleList", inlineLabel("Warning history requires hyessentialsx.warnings or profile detail access.", "#8193aa", 34));
            return;
        }

        List<WarningModel> warnings = new ArrayList<>(data.getWarnings());
        warnings.sort(Comparator.comparingLong(WarningModel::getCreatedAt).reversed());
        if (warnings.isEmpty()) {
            cmd.appendInline("#SelectedModuleList", inlineLabel("No warnings recorded.", "#8193aa", 28));
        } else {
            int shown = 0;
            for (WarningModel warning : warnings) {
                String reason = warning.getReason() == null || warning.getReason().isBlank() ? "No reason" : warning.getReason();
                String issuer = warning.getIssuer() == null || warning.getIssuer().isBlank() ? "Unknown" : warning.getIssuer();
                String status = warning.isActive() ? "Active" : "Inactive";
                String created = warning.getCreatedAt() <= 0L ? "unknown date" : formatDate(warning.getCreatedAt());
                appendModuleRow(cmd, status, reason + " | " + issuer + " | " + created);
                shown++;
                if (shown >= 8) break;
            }
        }
        appendModuleHeader(cmd, "Cases");
        appendCases(cmd, data, 8);
    }

    private void buildEconomyModule(@Nonnull UICommandBuilder cmd, @Nonnull UUID id) {
        cmd.set("#SelectedModuleTitle.Text", "Economy Inspector");
        if (context.economyManager() == null) {
            cmd.appendInline("#SelectedModuleList", inlineLabel("Economy is disabled.", "#8193aa", 28));
            return;
        }
        long balance = context.economyManager().getBalance(id);
        appendModuleRow(cmd, "Balance", context.economyManager().formatAmount(balance));
        appendModuleRow(cmd, "Compact", context.economyManager().formatAmountCompact(balance));
        appendModuleRow(cmd, "Starting", context.economyManager().formatAmount(context.economyManager().getStartingBalance()));
        appendModuleRow(cmd, "Exposure", countPlayerShopListings(id) + " shop listing(s), " + countPlayerAuctions(id) + " auction listing(s)");
        appendModuleRow(cmd, "Reset", canUse("hyessentialsx.ecoadmin") ? "Use Reset $ in Danger Zone." : "Missing hyessentialsx.ecoadmin");
    }

    private void buildTravelModule(@Nonnull UICommandBuilder cmd, @Nonnull PlayerDataModel data) {
        cmd.set("#SelectedModuleTitle.Text", "Travel Inspector");
        appendModuleRow(cmd, "Homes", data.getHomes().size() + " saved");
        appendModuleRow(cmd, "Warps", context.storage().getWarps().size() + " server warps");
        appendModuleRow(cmd, "Teleport", "Use the Travel action buttons below this module.");
        appendModuleHeader(cmd, "Homes");
        int shown = 0;
        for (HomeModel home : data.getHomes().values()) {
            if (home == null) continue;
            appendModuleRow(cmd, home.getName(), home.getWorldName() + " @ "
                    + compactLocation(home.getX(), home.getY(), home.getZ()));
            shown++;
            if (shown >= 6) break;
        }
        if (shown == 0) {
            cmd.appendInline("#SelectedModuleList", inlineLabel("No homes saved.", "#8193aa", 24));
        }
    }

    private void buildStatsModule(@Nonnull UICommandBuilder cmd, @Nonnull PlayerDataModel data) {
        cmd.set("#SelectedModuleTitle.Text", "Stats");
        appendModuleRow(cmd, "Deaths", String.valueOf(data.getStat("basic", "deaths")));
        appendModuleRow(cmd, "Messages", String.valueOf(data.getStat("basic", "messages")));
        appendModuleRow(cmd, "Player Kills", String.valueOf(data.getStat("combat", "player_kills")));
        appendModuleRow(cmd, "Playtime", TimeUtil.formatDurationSeconds(data.getPlaytimeSeconds()));
        appendModuleHeader(cmd, "Top Stats");
        List<Map.Entry<String, Long>> stats = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> category : data.getStats().entrySet()) {
            if (category.getValue() == null) continue;
            for (Map.Entry<String, Long> stat : category.getValue().entrySet()) {
                if (stat.getValue() != null && stat.getValue() > 0L) {
                    stats.add(Map.entry(category.getKey() + "." + stat.getKey(), stat.getValue()));
                }
            }
        }
        stats.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        int shown = 0;
        for (Map.Entry<String, Long> stat : stats) {
            appendModuleRow(cmd, stat.getKey(), String.valueOf(stat.getValue()));
            shown++;
            if (shown >= 8) break;
        }
        if (shown == 0) {
            cmd.appendInline("#SelectedModuleList", inlineLabel("No detailed stats recorded yet.", "#8193aa", 24));
        }
    }

    private void buildActivityModule(@Nonnull UICommandBuilder cmd, @Nonnull UUID id, @Nonnull PlayerDataModel data) {
        cmd.set("#SelectedModuleTitle.Text", "Timeline and Audit");
        appendModuleHeader(cmd, "Timeline");
        if (data.getFirstJoinAt() > 0L) appendModuleRow(cmd, "Joined", formatDate(data.getFirstJoinAt()));
        if (data.getLastJoinAt() > 0L) appendModuleRow(cmd, "Last Join", formatDate(data.getLastJoinAt()));
        appendCases(cmd, data, 8);
        appendModuleHeader(cmd, "Staff Notes");
        appendNotes(cmd, data, 5);
        appendModuleHeader(cmd, "Audit Log");
        int shown = 0;
        String idText = id.toString();
        for (StaffActivityEntryModel entry : context.storage().listRecentStaffActivity(40)) {
            if (entry == null || !idText.equals(entry.getTargetUuid())) continue;
            appendModuleRow(cmd, formatDate(entry.getCreatedAt()), formatActivity(entry));
            shown++;
            if (shown >= 8) break;
        }
        if (shown == 0) {
            cmd.appendInline("#SelectedModuleList", inlineLabel("No recent audit entries for this player.", "#8193aa", 24));
        }
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
            long warnings = context.storage().countActiveWarnings(row.uuid);
            if (!row.banned && !row.muted && !row.frozen && warnings <= 0L) {
                continue;
            }
            String warningText = warnings <= 0L ? "" : " / " + warnings + " warning" + (warnings == 1L ? "" : "s");
            cmd.appendInline("#ModerationQueue", "Label { Text: \"" + escapeInline(row.name + " - " + row.status + warningText)
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

        cmd.clear("#StaffActivityList");
        List<StaffActivityEntryModel> activity = context.storage().listRecentStaffActivity(8);
        if (activity.isEmpty()) {
            cmd.appendInline("#StaffActivityList", "Label { Text: \"No staff activity recorded yet.\"; "
                    + "Anchor: (Height: 24); Style: (FontSize: 10, TextColor: #8193aa, Wrap: true); }");
        } else {
            for (StaffActivityEntryModel entry : activity) {
                String line = formatActivity(entry);
                cmd.appendInline("#StaffActivityList", "Label { Text: \"" + escapeInline(line)
                        + "\"; Anchor: (Height: 30, Bottom: 3); Style: (FontSize: 10, TextColor: #c8d6ea, Wrap: true); }");
            }
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
            case "FilterAll" -> setFilter(PlayerFilter.ALL);
            case "FilterOnline" -> setFilter(PlayerFilter.ONLINE);
            case "FilterPunished" -> setFilter(PlayerFilter.PUNISHED);
            case "FilterWarned" -> setFilter(PlayerFilter.WARNED);
            case "FilterBalance" -> setFilter(PlayerFilter.HAS_BALANCE);
            case "FilterNew" -> setFilter(PlayerFilter.NEW);
            case "CycleSort" -> {
                playerSort = playerSort.next();
                playerPage = 0;
                status("Player sort: " + playerSort.label() + ".", "#8fb6e8");
                refresh();
            }
            case "ToggleActions" -> {
                actionsOpen = !actionsOpen;
                refresh();
            }
            case "CancelPending" -> {
                pendingAction = null;
                status("Pending action cancelled.", "#8fb6e8");
                refresh();
            }
            case "ConfirmPending" -> {
                confirmPending(ref, store);
                refresh();
            }
            case "ReasonSpam" -> setReasonPreset("Spam / chat flood");
            case "ReasonHarassment" -> setReasonPreset("Harassment or abusive behavior");
            case "ReasonExploiting" -> setReasonPreset("Exploiting / unfair advantage");
            case "ReasonChargeback" -> setReasonPreset("Chargeback / payment dispute");
            case "ReasonAlt" -> setReasonPreset("Alt abuse / ban evasion");
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
            case "Warn" -> {
                warnSelected();
                refresh();
            }
            case "AddNote" -> {
                addNoteSelected();
                refresh();
            }
            case "ClearWarnings" -> {
                requestConfirmation("ClearWarnings");
                refresh();
            }
            case "Unban" -> {
                unbanSelected();
                refresh();
            }
            case "Ban" -> {
                requestConfirmation("Ban");
                refresh();
            }
            case "ResetBalance" -> {
                requestConfirmation("ResetBalance");
                refresh();
            }
            case "TeleportTo" -> {
                teleportToSelected(ref, store);
                refresh();
            }
            case "TeleportHere" -> {
                teleportHereSelected(ref, store);
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

    private void setFilter(@Nonnull PlayerFilter filter) {
        playerFilter = filter;
        playerPage = 0;
        status("Player filter: " + filter.label() + ".", "#8fb6e8");
        refresh();
    }

    private void setReasonPreset(@Nonnull String reason) {
        actionReasonInput = reason;
        status("Reason preset selected: " + reason + ".", "#8fb6e8");
        refresh();
    }

    private void requestConfirmation(@Nonnull String action) {
        if (requireSelected() == null) return;
        pendingAction = action;
        actionsOpen = true;
        status("Confirm " + pendingText(action), "#ffb86b");
    }

    private void confirmPending(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        String action = pendingAction;
        pendingAction = null;
        if (action == null) {
            status("No pending action.", "#8fb6e8");
            return;
        }
        switch (action) {
            case "Ban" -> banSelected();
            case "ClearWarnings" -> clearWarningsSelected();
            case "ResetBalance" -> resetBalanceSelected();
            default -> status("Unknown pending action: " + action, "#ff7d7d");
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
        logActivity("freeze", id, target.getUsername(), "");
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
        logActivity("unfreeze", id, resolveDisplayName(id), "");
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
        logActivity("mute", id, name, mute.getReason());
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
        logActivity("unmute", id, resolveDisplayName(id), "");
        status("Unmuted " + resolveDisplayName(id) + ".", "#55d98b");
    }

    private void warnSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.warn")) {
            status("Missing permission: hyessentialsx.warn", "#ff7d7d");
            return;
        }
        String name = resolveDisplayName(id);
        String reason = actionReasonInput == null ? "" : actionReasonInput.trim();
        if (reason.isBlank()) {
            reason = "Warned from admin command center.";
        }
        WarningModel warning = new WarningModel(UUID.randomUUID().toString(), name, playerRef.getUsername(), reason,
                System.currentTimeMillis(), 0L);
        context.storage().addWarning(id, warning);
        PlayerRef target = Universe.get().getPlayer(id);
        if (target != null) {
            Messages.sendPrefixedKey(target, "warn.target", Map.of("reason", reason));
        }
        logActivity("warn", id, name, reason);
        status("Warned " + name + ".", "#55d98b");
    }

    private void addNoteSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.ui.playerdetails") && !canUse("hyessentialsx.whois")) {
            status("Missing profile detail permission for staff notes.", "#ff7d7d");
            return;
        }
        String note = actionReasonInput == null ? "" : actionReasonInput.trim();
        if (note.isBlank()) {
            status("Enter a note in the Action Reason field first.", "#ffb86b");
            return;
        }
        String name = resolveDisplayName(id);
        context.storage().addStaffNote(id, new StaffNoteModel(
                UUID.randomUUID().toString(),
                playerRef.getUsername(),
                note,
                System.currentTimeMillis()
        ));
        logActivity("note", id, name, note);
        actionReasonInput = "";
        profileTab = ProfileTab.ACTIVITY;
        status("Added staff note for " + name + ".", "#55d98b");
    }

    private void clearWarningsSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.clearwarnings")) {
            status("Missing permission: hyessentialsx.clearwarnings", "#ff7d7d");
            return;
        }
        String name = resolveDisplayName(id);
        int count = context.storage().clearWarnings(id);
        logActivity("clearwarnings", id, name, String.valueOf(count));
        status("Cleared " + count + " warning(s) for " + name + ".", "#55d98b");
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
        logActivity("unban", id, resolveDisplayName(id), "");
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
        String reason = actionReasonInput == null ? "" : actionReasonInput.trim();
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

        actionReasonInput = "";
        logActivity("ban", id, name, reason);
        status("Banned " + name + ".", "#55d98b");
    }

    private void resetBalanceSelected() {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.ecoadmin")) {
            status("Missing permission: hyessentialsx.ecoadmin", "#ff7d7d");
            return;
        }
        if (context.economyManager() == null) {
            status("Economy is unavailable.", "#ffb86b");
            return;
        }
        String name = resolveDisplayName(id);
        long previous = context.economyManager().getBalance(id);
        context.economyManager().setBalance(id, 0L);
        logActivity("reset-balance", id, name, "Previous " + context.economyManager().formatAmount(previous));
        profileTab = ProfileTab.ECONOMY;
        status("Reset " + name + "'s balance.", "#55d98b");
    }

    private void teleportToSelected(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.tphere") && !canUse("hyessentialsx.jumpto")) {
            status("Missing teleport permission.", "#ff7d7d");
            return;
        }
        if (id.equals(playerRef.getUuid())) {
            status("You are already selected.", "#ffb86b");
            return;
        }
        PlayerRef target = Universe.get().getPlayer(id);
        if (target == null) {
            status("Teleport requires the target to be online.", "#ffb86b");
            return;
        }
        String err = TeleportationUtil.teleportToPlayer(store, ref, target);
        if (err != null) {
            status(err, "#ff7d7d");
            return;
        }
        logActivity("teleport-to", id, target.getUsername(), "");
        status("Teleported to " + target.getUsername() + ".", "#55d98b");
    }

    private void teleportHereSelected(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID id = requireSelected();
        if (id == null) return;
        if (!canUse("hyessentialsx.tphere")) {
            status("Missing permission: hyessentialsx.tphere", "#ff7d7d");
            return;
        }
        if (id.equals(playerRef.getUuid())) {
            status("You cannot bring yourself to yourself.", "#ffb86b");
            return;
        }
        PlayerRef target = Universe.get().getPlayer(id);
        if (target == null) {
            status("Bring Here requires the target to be online.", "#ffb86b");
            return;
        }
        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef == null || targetRef.getStore() == null) {
            status("Could not access target entity.", "#ff7d7d");
            return;
        }
        String err = TeleportationUtil.teleportToPlayer(targetRef.getStore(), targetRef, playerRef);
        if (err != null) {
            status(err, "#ff7d7d");
            return;
        }
        Messages.sendPrefixedKey(target, "tphere.target", Map.of("player", playerRef.getUsername()));
        logActivity("teleport-here", id, target.getUsername(), "");
        status("Brought " + target.getUsername() + " to you.", "#55d98b");
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
        ProfileTab returnProfileTab = profileTab;
        PlayerFilter returnFilter = playerFilter;
        PlayerSort returnSort = playerSort;
        String returnSearch = playerSearch;
        int returnPage = playerPage;
        UUID returnSelectedId = selectedPlayerId;
        String returnSelectedName = selectedPlayerName;
        String returnActionReason = actionReasonInput;
        boolean returnActionsOpen = actionsOpen;
        String returnPendingAction = pendingAction;
        return (player, ref, store) -> {
            HyEssentialsXDashboardUI page = new HyEssentialsXDashboardUI(playerRef, context);
            page.currentTab = returnTab;
            page.profileTab = returnProfileTab;
            page.playerFilter = returnFilter;
            page.playerSort = returnSort;
            page.playerSearch = returnSearch;
            page.playerPage = returnPage;
            page.selectedPlayerId = returnSelectedId;
            page.selectedPlayerName = returnSelectedName;
            page.actionReasonInput = returnActionReason;
            page.actionsOpen = returnActionsOpen;
            page.pendingAction = returnPendingAction;
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
            long warnings = context.storage().countActiveWarnings(id);
            String status = stateSummary(online, banned, muted, frozen);
            PlayerRow row = new PlayerRow(id, name, online, banned, muted, frozen, balance,
                    data.getPlaytimeSeconds(), warnings, data.getLastSeenAt(), status);
            if (!matchesFilter(row)) {
                continue;
            }
            rows.add(row);
        }
        rows.sort(playerSort.comparator());
        return rows;
    }

    private boolean matchesFilter(@Nonnull PlayerRow row) {
        return switch (playerFilter) {
            case ALL -> true;
            case ONLINE -> row.online;
            case PUNISHED -> row.banned || row.muted || row.frozen;
            case WARNED -> row.warnings > 0L;
            case HAS_BALANCE -> row.balance > 0L;
            case NEW -> row.playtimeSeconds < 3600L;
        };
    }

    @Nonnull
    private String playerStateLine(@Nonnull UUID id, @Nonnull PlayerDataModel data) {
        boolean canViewDetails = canUse("hyessentialsx.ui.playerdetails") || canUse("hyessentialsx.whois");
        String balance = !canViewDetails ? "hidden"
                : context.economyManager() == null ? "Economy disabled" : context.economyManager().formatAmount(context.economyManager().getBalance(id));
        int mail = canViewDetails ? data.getMailInbox().size() : 0;
        int homes = canViewDetails ? data.getHomes().size() : 0;
        long warnings = context.storage().countActiveWarnings(id);
        return stateSummary(Universe.get().getPlayer(id) != null,
                context.banManager().isBanned(id),
                context.muteManager().isMuted(id),
                context.freezeManager().isFrozenOrStored(id))
                + " | Balance: " + balance
                + " | Playtime: " + TimeUtil.formatDurationSeconds(data.getPlaytimeSeconds())
                + " | Warnings: " + warnings
                + (canViewDetails ? " | Homes: " + homes + " | Inbox: " + mail : "");
    }

    @Nonnull
    private String selectedProfileLine(@Nonnull UUID id, @Nonnull PlayerDataModel data, @Nonnull String name) {
        if (!canUse("hyessentialsx.ui.playerdetails") && !canUse("hyessentialsx.whois")) {
            return "Limited view. Grant hyessentialsx.ui.playerdetails or hyessentialsx.whois for full profile details.";
        }
        String currentIp = data.getCurrentIp();
        String ip = !canUse("hyessentialsx.iphistory") || currentIp == null || currentIp.isBlank() ? "hidden" : currentIp;
        String latestWarning = "none";
        List<WarningModel> warnings = new ArrayList<>(data.getWarnings());
        warnings.sort(Comparator.comparingLong(WarningModel::getCreatedAt).reversed());
        if (!warnings.isEmpty()) {
            WarningModel warning = warnings.get(0);
            String reason = warning.getReason() == null || warning.getReason().isBlank() ? "No reason" : warning.getReason();
            latestWarning = reason + " by " + (warning.getIssuer() == null ? "Unknown" : warning.getIssuer());
        }
        String topStats = topStats(data);
        return "IP: " + ip
                + " | Warnings: " + data.getWarnings().size()
                + " | Latest: " + latestWarning
                + " | Warps: " + context.storage().getWarps().size()
                + " | Stats: " + topStats
                + " | Commands: /whois " + name + " /seen " + name + " /iphistory " + name;
    }

    @Nonnull
    private String topStats(@Nonnull PlayerDataModel data) {
        long deaths = data.getStat("basic", "deaths");
        long messages = data.getStat("basic", "messages");
        long kills = data.getStat("combat", "player_kills");
        return "deaths " + deaths + ", messages " + messages + ", kills " + kills;
    }

    @Nonnull
    private String formatActivity(@Nonnull StaffActivityEntryModel entry) {
        String actor = entry.getActor() == null || entry.getActor().isBlank() ? "Unknown" : entry.getActor();
        String action = entry.getAction() == null || entry.getAction().isBlank() ? "action" : entry.getAction();
        String target = entry.getTargetName() == null || entry.getTargetName().isBlank() ? "server" : entry.getTargetName();
        String detail = entry.getDetail() == null || entry.getDetail().isBlank() ? "" : " - " + entry.getDetail();
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - entry.getCreatedAt()) / 1000L);
        return TimeUtil.formatDurationSeconds(ageSeconds) + " ago: " + actor + " " + action + " " + target + detail;
    }

    private void logActivity(@Nonnull String action,
                             @Nullable UUID targetId,
                             @Nullable String targetName,
                             @Nullable String detail) {
        StaffActionUtil.log(context.storage(), playerRef.getUsername(), action, targetId, targetName, detail);
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

    private void appendModuleHeader(@Nonnull UICommandBuilder cmd, @Nonnull String text) {
        cmd.appendInline("#SelectedModuleList", "Label { Text: \"" + escapeInline(text)
                + "\"; Anchor: (Height: 20, Bottom: 4, Top: 4); "
                + "Style: (FontSize: 10, TextColor: #8fb6e8, RenderBold: true); }");
    }

    private void appendModuleRow(@Nonnull UICommandBuilder cmd,
                                 @Nonnull String label,
                                 @Nonnull String value) {
        cmd.appendInline("#SelectedModuleList", "Group { LayoutMode: Top; Anchor: (Height: 42, Bottom: 5); "
                + "Padding: (Top: 4, Bottom: 4, Left: 6, Right: 6); Background: (Color: #151d2a); "
                + "OutlineColor: #2f4a72; OutlineSize: 1; "
                + "Label { Text: \"" + escapeInline(label) + "\"; Anchor: (Height: 14, Bottom: 2); "
                + "Style: (FontSize: 9, TextColor: #8fb6e8, RenderBold: true, ShrinkTextToFit: true, MinShrinkTextToFitFontSize: 7); } "
                + "Label { Text: \"" + escapeInline(value) + "\"; Anchor: (Height: 16); "
                + "Style: (FontSize: 10, TextColor: #d7e7ff, ShrinkTextToFit: true, MinShrinkTextToFitFontSize: 7); } }");
    }

    @Nonnull
    private String inlineLabel(@Nonnull String text, @Nonnull String color, int height) {
        return "Label { Text: \"" + escapeInline(text) + "\"; Anchor: (Height: " + height + "); "
                + "Style: (FontSize: 10, TextColor: " + color + ", Wrap: true); }";
    }

    private void appendCases(@Nonnull UICommandBuilder cmd, @Nonnull PlayerDataModel data, int limit) {
        List<StaffCaseModel> cases = new ArrayList<>(data.getStaffCases());
        cases.removeIf(Objects::isNull);
        cases.sort(Comparator.comparingLong(StaffCaseModel::getCreatedAt).reversed());
        int shown = 0;
        for (StaffCaseModel staffCase : cases) {
            String id = staffCase.getId() == null || staffCase.getId().isBlank() ? "CASE-????" : staffCase.getId();
            String type = staffCase.getType() == null || staffCase.getType().isBlank() ? "action" : staffCase.getType();
            String actor = staffCase.getActor() == null || staffCase.getActor().isBlank() ? "Unknown" : staffCase.getActor();
            String detail = staffCase.getDetail() == null || staffCase.getDetail().isBlank() ? "" : " | " + staffCase.getDetail();
            appendModuleRow(cmd, id + " " + type, actor + " | " + formatDate(staffCase.getCreatedAt()) + detail);
            shown++;
            if (shown >= limit) break;
        }
        if (shown == 0) {
            cmd.appendInline("#SelectedModuleList", inlineLabel("No cases recorded yet.", "#8193aa", 24));
        }
    }

    private void appendNotes(@Nonnull UICommandBuilder cmd, @Nonnull PlayerDataModel data, int limit) {
        List<StaffNoteModel> notes = new ArrayList<>(data.getStaffNotes());
        notes.removeIf(Objects::isNull);
        notes.sort(Comparator.comparingLong(StaffNoteModel::getCreatedAt).reversed());
        int shown = 0;
        for (StaffNoteModel note : notes) {
            String actor = note.getActor() == null || note.getActor().isBlank() ? "Unknown" : note.getActor();
            String text = note.getNote() == null || note.getNote().isBlank() ? "Empty note" : note.getNote();
            appendModuleRow(cmd, actor + " | " + formatDate(note.getCreatedAt()), text);
            shown++;
            if (shown >= limit) break;
        }
        if (shown == 0) {
            cmd.appendInline("#SelectedModuleList", inlineLabel("No staff notes yet. Enter a note and click Add Note.", "#8193aa", 28));
        }
    }

    private int countPlayerShopListings(@Nonnull UUID id) {
        String uuid = id.toString();
        int count = 0;
        for (ShopModel shop : context.storage().getShops().values()) {
            if (shop != null && shop.isPlayerShop() && uuid.equals(shop.getOwnerUuid())) {
                count += Math.max(1, shop.getTrades().size());
            }
        }
        return count;
    }

    private int countPlayerAuctions(@Nonnull UUID id) {
        if (context.auctionHouseManager() == null) {
            return 0;
        }
        String uuid = id.toString();
        int count = 0;
        for (AuctionListingModel listing : context.auctionHouseManager().listActive()) {
            if (listing != null && uuid.equals(listing.getSellerUuid())) {
                count++;
            }
        }
        return count;
    }

    @Nonnull
    private String compactLocation(double x, double y, double z) {
        return Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z);
    }

    private boolean recentIpChange(@Nonnull PlayerDataModel data) {
        List<IpHistoryModel> history = data.getIpHistory();
        if (history.size() < 2) {
            return false;
        }
        long newest = 0L;
        for (IpHistoryModel entry : history) {
            if (entry != null) {
                newest = Math.max(newest, entry.getLastUsed());
            }
        }
        return newest > 0L && System.currentTimeMillis() - newest <= TimeUnit.DAYS.toMillis(7);
    }

    private boolean isNewPlayer(@Nonnull PlayerDataModel data) {
        return data.getPlaytimeSeconds() < 3600L
                || (data.getFirstJoinAt() > 0L && System.currentTimeMillis() - data.getFirstJoinAt() <= TimeUnit.DAYS.toMillis(2));
    }

    private long highBalanceThreshold() {
        long highest = 0L;
        for (UUID id : context.storage().listPlayerIds()) {
            if (context.economyManager() != null) {
                highest = Math.max(highest, context.economyManager().getBalance(id));
            }
        }
        return Math.max(highest / 2L, context.economyManager() == null ? 0L : context.economyManager().getStartingBalance() * 10L);
    }

    private boolean possibleAltMatch(@Nonnull UUID id, @Nonnull PlayerDataModel data) {
        String ip = data.getCurrentIp();
        if (ip == null || ip.isBlank()) {
            return false;
        }
        for (UUID other : context.storage().listPlayerIds()) {
            if (id.equals(other)) continue;
            PlayerDataModel otherData = context.storage().getPlayerData(other);
            if (ip.equals(otherData.getCurrentIp())) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private String riskColor(@Nonnull String flag) {
        String lower = flag.toLowerCase(Locale.ROOT);
        if (lower.contains("banned") || lower.contains("alt")) return "#ff7d7d";
        if (lower.contains("muted") || lower.contains("frozen") || lower.contains("warning")) return "#d5c47d";
        if (lower.contains("high") || lower.contains("recent") || lower.contains("new")) return "#ffb86b";
        if (lower.contains("low")) return "#54e39e";
        return "#8fd3ff";
    }

    @Nonnull
    private String pendingText(@Nonnull String action) {
        String target = selectedPlayerId == null ? "selected player" : resolveDisplayName(selectedPlayerId);
        return switch (action) {
            case "Ban" -> "Ban " + target + " with reason: " + actionReasonOrDefault();
            case "ClearWarnings" -> "Clear all warnings for " + target + ".";
            case "ResetBalance" -> "Reset " + target + "'s balance to 0.";
            default -> action + " for " + target + ".";
        };
    }

    @Nonnull
    private String actionReasonOrDefault() {
        String reason = actionReasonInput == null ? "" : actionReasonInput.trim();
        return reason.isBlank() ? "No reason" : reason;
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
        return text.replace("\\", "\\\\")
                .replace("\"", "'")
                .replace("\n", " ")
                .replace("\r", " ");
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

    @Nonnull
    private ProfileTab parseProfileTab(@Nonnull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "punishments" -> ProfileTab.PUNISHMENTS;
            case "economy" -> ProfileTab.ECONOMY;
            case "travel" -> ProfileTab.TRAVEL;
            case "stats" -> ProfileTab.STATS;
            case "activity" -> ProfileTab.ACTIVITY;
            default -> ProfileTab.OVERVIEW;
        };
    }

    private enum Tab {
        OVERVIEW,
        PLAYERS,
        MODERATION,
        SYSTEMS,
        LAUNCH
    }

    private enum ProfileTab {
        OVERVIEW,
        PUNISHMENTS,
        ECONOMY,
        TRAVEL,
        STATS,
        ACTIVITY
    }

    private enum PlayerFilter {
        ALL("All"),
        ONLINE("Online"),
        PUNISHED("Punished"),
        WARNED("Warned"),
        HAS_BALANCE("Has Balance"),
        NEW("New");

        private final String label;

        PlayerFilter(@Nonnull String label) {
            this.label = label;
        }

        @Nonnull
        private String label() {
            return label;
        }
    }

    private enum PlayerSort {
        NAME("Name"),
        ONLINE("Online"),
        BALANCE("Balance"),
        PLAYTIME("Playtime"),
        WARNINGS("Warnings"),
        LAST_SEEN("Last Seen");

        private final String label;

        PlayerSort(@Nonnull String label) {
            this.label = label;
        }

        @Nonnull
        private String label() {
            return label;
        }

        @Nonnull
        private PlayerSort next() {
            PlayerSort[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        @Nonnull
        private Comparator<PlayerRow> comparator() {
            Comparator<PlayerRow> onlineFirst = Comparator.comparing((PlayerRow row) -> !row.online);
            Comparator<PlayerRow> name = Comparator.comparing(row -> row.name.toLowerCase(Locale.ROOT));
            return switch (this) {
                case NAME -> onlineFirst.thenComparing(name);
                case ONLINE -> onlineFirst.thenComparing(name);
                case BALANCE -> Comparator.comparingLong((PlayerRow row) -> row.balance).reversed().thenComparing(name);
                case PLAYTIME -> Comparator.comparingLong((PlayerRow row) -> row.playtimeSeconds).reversed().thenComparing(name);
                case WARNINGS -> Comparator.comparingLong((PlayerRow row) -> row.warnings).reversed().thenComparing(name);
                case LAST_SEEN -> Comparator.comparingLong((PlayerRow row) -> row.lastSeenAt).reversed().thenComparing(name);
            };
        }
    }

    private record PlayerRow(UUID uuid,
                             String name,
                             boolean online,
                             boolean banned,
                             boolean muted,
                             boolean frozen,
                             long balance,
                             long playtimeSeconds,
                             long warnings,
                             long lastSeenAt,
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
                .append(new KeyedCodec<>("ProfileTab", Codec.STRING), (d, v) -> d.profileTab = v, d -> d.profileTab).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("PlayerAction", Codec.STRING), (d, v) -> d.playerAction = v, d -> d.playerAction).add()
                .append(new KeyedCodec<>("PlayerUuid", Codec.STRING), (d, v) -> d.playerUuid = v, d -> d.playerUuid).add()
                .append(new KeyedCodec<>("PlayerName", Codec.STRING), (d, v) -> d.playerName = v, d -> d.playerName).add()
                .append(new KeyedCodec<>("@PlayerSearch", Codec.STRING), (d, v) -> d.playerSearch = v, d -> d.playerSearch).add()
                .append(new KeyedCodec<>("@ActionReason", Codec.STRING), (d, v) -> d.actionReason = v, d -> d.actionReason).add()
                .build();

        private String tab;
        private String profileTab;
        private String action;
        private String playerAction;
        private String playerUuid;
        private String playerName;
        private String playerSearch;
        private String actionReason;
    }
}
