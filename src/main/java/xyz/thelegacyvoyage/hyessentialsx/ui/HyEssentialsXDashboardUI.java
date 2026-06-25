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
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerWarpModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffActivityEntryModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffCaseModel;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffNoteModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningEscalationRuleModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.economy.EcoAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
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
    private static final int CONFIG_RULE_ROW_COUNT = 8;
    private static final int CONFIG_PANEL_FIELD_COUNT = 6;
    private static final int MODERATION_RULE_BUTTON_COUNT = 3;
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
    private String warningExpiryInput = "";
    private boolean actionsOpen = true;
    private String configRuleIdInput = "";
    private String configRuleNameInput = "";
    private String configThresholdInput = "3";
    private String configActionInput = "MUTE";
    private String configDurationInput = "1h";
    private String configWindowInput = "7d";
    private String configDetailInput = "Automatic warning escalation.";
    private ConfigSection configSection = ConfigSection.PLAYER_WARPS;
    private ConfigSection loadedConfigSection;
    private final String[] configPanelInputs = new String[CONFIG_PANEL_FIELD_COUNT];
    @Nullable
    private String selectedConfigRuleId;
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
        if ("SelectConfigRule".equals(data.configRuleAction) && data.configRuleId != null) {
            selectConfigRule(data.configRuleId);
            refresh();
            return;
        }
        if (data.configRuleIdInput != null) {
            configRuleIdInput = data.configRuleIdInput.trim();
        }
        if (data.configRuleNameInput != null) {
            configRuleNameInput = data.configRuleNameInput.trim();
        }
        if (data.configThresholdInput != null) {
            configThresholdInput = data.configThresholdInput.trim();
        }
        if (data.configDurationInput != null) {
            configDurationInput = data.configDurationInput.trim();
        }
        if (data.configWindowInput != null) {
            configWindowInput = data.configWindowInput.trim();
        }
        if (data.configDetailInput != null) {
            configDetailInput = data.configDetailInput.trim();
        }
        for (int i = 0; i < CONFIG_PANEL_FIELD_COUNT; i++) {
            String value = data.configPanelInput(i);
            if (value != null) {
                configPanelInputs[i] = value.trim();
            }
        }
        if (data.actionReason != null) {
            actionReasonInput = data.actionReason;
            return;
        }
        if (data.warningExpiry != null) {
            warningExpiryInput = data.warningExpiry.trim();
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
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabConfig",
                EventData.of("Tab", "Config"), false);
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
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#WarnExpiryInput",
                EventData.of("@WarningExpiry", "#WarnExpiryInput.Value"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenEconomyButton",
                EventData.of("Action", "OpenEconomy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenPlaytimeButton",
                EventData.of("Action", "OpenPlaytime"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenBansButton",
                EventData.of("Action", "OpenBans"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenBansModerationButton",
                EventData.of("Action", "OpenBans"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EscalationRule0Button",
                EventData.of("Action", "ToggleEscalationSlot0"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EscalationRule1Button",
                EventData.of("Action", "ToggleEscalationSlot1"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EscalationRule2Button",
                EventData.of("Action", "ToggleEscalationSlot2"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EscalationDefaultsButton",
                EventData.of("Action", "ResetEscalations"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ConfigRuleIdInput",
                EventData.of("@ConfigRuleIdInput", "#ConfigRuleIdInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ConfigRuleNameInput",
                EventData.of("@ConfigRuleNameInput", "#ConfigRuleNameInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ConfigThresholdInput",
                EventData.of("@ConfigThresholdInput", "#ConfigThresholdInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ConfigDurationInput",
                EventData.of("@ConfigDurationInput", "#ConfigDurationInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ConfigWindowInput",
                EventData.of("@ConfigWindowInput", "#ConfigWindowInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ConfigDetailInput",
                EventData.of("@ConfigDetailInput", "#ConfigDetailInput.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigActionButton",
                EventData.of("Action", "CycleConfigAction"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NewConfigRuleButton",
                EventData.of("Action", "NewConfigRule"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveConfigRuleButton",
                EventData.of("Action", "SaveConfigRule"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleConfigRuleButton",
                EventData.of("Action", "ToggleConfigRule"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteConfigRuleButton",
                EventData.of("Action", "DeleteConfigRule"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ResetConfigRulesButton",
                EventData.of("Action", "ResetEscalations"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigSectionWarpsButton",
                EventData.of("Action", "ConfigSectionPlayerWarps"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigSectionEconomyButton",
                EventData.of("Action", "ConfigSectionEconomy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigSectionPlaytimeButton",
                EventData.of("Action", "ConfigSectionPlaytime"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigSectionShopsButton",
                EventData.of("Action", "ConfigSectionShops"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigSectionTravelButton",
                EventData.of("Action", "ConfigSectionTravel"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigSectionHomesButton",
                EventData.of("Action", "ConfigSectionHomes"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigSectionChatButton",
                EventData.of("Action", "ConfigSectionChat"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigSectionScoreboardButton",
                EventData.of("Action", "ConfigSectionScoreboard"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveConfigPanelButton",
                EventData.of("Action", "SaveConfigPanel"), false);
        for (int i = 0; i < CONFIG_PANEL_FIELD_COUNT; i++) {
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ConfigField" + i + "Input",
                    EventData.of("@ConfigPanelInput" + i, "#ConfigField" + i + "Input.Value"), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfigField" + i + "Button",
                    EventData.of("Action", "ToggleConfigPanelField" + i), false);
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenShopsButton",
                EventData.of("Action", "OpenShops"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenAuctionButton",
                EventData.of("Action", "OpenAuction"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OpenAnnouncementsButton",
                EventData.of("Action", "OpenAnnouncements"), false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        cmd.set("#VersionText.Text", "v" + PluginInfoUtil.getVersion());
        cmd.set("#StatusText.Text", statusMessage);
        cmd.set("#StatusText.Style.TextColor", statusColor);

        cmd.set("#TabOverview.Text", currentTab == Tab.OVERVIEW ? "OVERVIEW *" : "OVERVIEW");
        cmd.set("#TabPlayers.Text", currentTab == Tab.PLAYERS ? "PLAYERS *" : "PLAYERS");
        cmd.set("#TabModeration.Text", currentTab == Tab.MODERATION ? "MODERATION *" : "MODERATION");
        cmd.set("#TabConfig.Text", currentTab == Tab.CONFIG ? "CONFIG *" : "CONFIG");
        cmd.set("#TabSystems.Text", currentTab == Tab.SYSTEMS ? "SYSTEMS *" : "SYSTEMS");
        cmd.set("#TabLaunch.Text", currentTab == Tab.LAUNCH ? "LAUNCH *" : "LAUNCH");

        cmd.set("#OverviewContent.Visible", currentTab == Tab.OVERVIEW);
        cmd.set("#PlayersContent.Visible", currentTab == Tab.PLAYERS);
        cmd.set("#ModerationContent.Visible", currentTab == Tab.MODERATION);
        cmd.set("#ConfigContent.Visible", currentTab == Tab.CONFIG);
        cmd.set("#SystemsContent.Visible", currentTab == Tab.SYSTEMS);
        cmd.set("#LaunchContent.Visible", currentTab == Tab.LAUNCH);

        buildOverview(cmd);
        buildPlayers(cmd, evt);
        buildModeration(cmd);
        if (currentTab == Tab.CONFIG) {
            buildConfig(cmd, evt);
        }
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
        cmd.set("#WarnExpiryInput.Value", warningExpiryInput);
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
                String expiry = warning.getExpiresAt() <= 0L ? "permanent" : "expires " + TimeUtil.formatRemaining(warning.getExpiresAt());
                appendModuleRow(cmd, status, reason + " | " + issuer + " | " + created + " | " + expiry);
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
        appendModuleRow(cmd, "Player Warps", data.getPlayerWarps().size() + " owned");
        appendModuleRow(cmd, "Teleport", "Use the Travel action buttons below this module.");
        appendModuleHeader(cmd, "Player Warps");
        int warpShown = 0;
        for (PlayerWarpModel warp : data.getPlayerWarps().values()) {
            if (warp == null) continue;
            String state = (warp.isPublicWarp() ? "Public" : "Private")
                    + (warp.isApproved() ? "" : " / Pending")
                    + (warp.isEnabled() ? "" : " / Hidden");
            appendModuleRow(cmd, warp.getName(), state + " | " + warp.getWorldName() + " @ "
                    + compactLocation(warp.getX(), warp.getY(), warp.getZ()));
            warpShown++;
            if (warpShown >= 5) break;
        }
        if (warpShown == 0) {
            cmd.appendInline("#SelectedModuleList", inlineLabel("No player warps owned.", "#8193aa", 24));
        }
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

        buildEscalationRules(cmd);
    }

    private void buildEscalationRules(@Nonnull UICommandBuilder cmd) {
        cmd.clear("#EscalationRuleList");
        List<WarningEscalationRuleModel> rules = sortedWarningRules();
        if (rules.isEmpty()) {
            cmd.appendInline("#EscalationRuleList", inlineLabel("No escalation rules configured.", "#8193aa", 24));
        } else {
            for (WarningEscalationRuleModel rule : rules) {
                String duration = rule.getDurationSeconds() <= 0L ? "permanent" : compactDuration(rule.getDurationSeconds());
                String window = rule.getWindowSeconds() <= 0L ? "all time" : compactDuration(rule.getWindowSeconds());
                cmd.appendInline("#EscalationRuleList", "Label { Text: \"" + escapeInline((rule.isEnabled() ? "ON " : "OFF ")
                        + rule.getName() + " | " + rule.getAction() + " | " + duration + " | window " + window)
                        + "\"; Anchor: (Height: 24, Bottom: 3); Style: (FontSize: 10, TextColor: "
                        + (rule.isEnabled() ? "#c8d6ea" : "#6f7d90") + ", Wrap: true); }");
            }
        }
        for (int i = 0; i < MODERATION_RULE_BUTTON_COUNT; i++) {
            String selector = "#EscalationRule" + i + "Button";
            if (i >= rules.size()) {
                cmd.set(selector + ".Text", "No Rule");
                cmd.set(selector + ".Disabled", true);
                continue;
            }
            WarningEscalationRuleModel rule = rules.get(i);
            cmd.set(selector + ".Text", moderationRuleButtonText(rule));
            cmd.set(selector + ".Disabled", !canManageWarnRules());
        }
    }

    private void buildConfig(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        List<WarningEscalationRuleModel> rules = sortedWarningRules();

        if ((selectedConfigRuleId == null || context.warningEscalationManager().getRule(selectedConfigRuleId) == null)
                && !rules.isEmpty() && configRuleIdInput.isBlank()) {
            loadConfigRule(rules.get(0));
        }

        cmd.set("#ConfigRuleSummary.Text", rules.size() + " persisted warning escalation rule(s). Changes here are shared with /warnrules."
                + (rules.size() > CONFIG_RULE_ROW_COUNT ? " Showing first " + CONFIG_RULE_ROW_COUNT + "." : ""));
        cmd.set("#ConfigRuleEmptyLabel.Visible", rules.isEmpty());
        for (int i = 0; i < CONFIG_RULE_ROW_COUNT; i++) {
            String row = "#ConfigRuleRow" + i;
            if (i >= rules.size()) {
                cmd.set(row + ".Visible", false);
                continue;
            }
            WarningEscalationRuleModel rule = rules.get(i);
            String id = rule.getId();
            boolean selected = selectedConfigRuleId != null && selectedConfigRuleId.equalsIgnoreCase(id);
            String duration = rule.getDurationSeconds() <= 0L ? "0" : compactDuration(rule.getDurationSeconds());
            String window = rule.getWindowSeconds() <= 0L ? "0" : compactDuration(rule.getWindowSeconds());
            cmd.set(row + ".Visible", true);
            cmd.set(row + ".Background.Color", selected ? "#173055" : "#151d2a");
            cmd.set("#ConfigRule" + i + "Name.Text", (rule.isEnabled() ? "ON " : "OFF ") + rule.getName());
            cmd.set("#ConfigRule" + i + "Name.Style.TextColor", rule.isEnabled() ? "#d7e7ff" : "#74849b");
            cmd.set("#ConfigRule" + i + "Meta.Text", rule.getThreshold() + " warn(s) -> " + rule.getAction()
                    + " | " + duration + " | " + window);
            cmd.set("#ConfigRule" + i + "Button.Text", selected ? "Editing" : "Edit");
            evt.addEventBinding(CustomUIEventBindingType.Activating,
                    "#ConfigRule" + i + "Button",
                    EventData.of("ConfigRuleAction", "SelectConfigRule").append("ConfigRuleId", id), false);
        }

        String action = normalizeConfigAction(configActionInput);
        cmd.set("#ConfigRuleIdInput.Value", configRuleIdInput);
        cmd.set("#ConfigRuleNameInput.Value", configRuleNameInput);
        cmd.set("#ConfigThresholdInput.Value", configThresholdInput);
        cmd.set("#ConfigActionButton.Text", action);
        cmd.set("#ConfigDurationInput.Value", configDurationInput);
        cmd.set("#ConfigWindowInput.Value", configWindowInput);
        cmd.set("#ConfigDetailInput.Value", configDetailInput);
        cmd.set("#ConfigDetailLabel.Text", action.equals("COMMAND") ? "Command Case Detail" : "Punishment Reason");

        WarningEscalationRuleModel selected = selectedConfigRuleId == null ? null : context.warningEscalationManager().getRule(selectedConfigRuleId);
        boolean canEdit = canManageWarnRules();
        cmd.set("#ConfigEditorHint.Text", canEdit
                ? "Edit a selected rule or create a new one. Durations accept 30m, 2h, 7d, 1w, none, or 0."
                : "Missing permission: hyessentialsx.warnrules or hyessentialsx.admin.");
        cmd.set("#ToggleConfigRuleButton.Text", selected != null && selected.isEnabled() ? "Disable" : "Enable");
        cmd.set("#ToggleConfigRuleButton.Disabled", !canEdit || selected == null);
        cmd.set("#SaveConfigRuleButton.Disabled", !canEdit);
        cmd.set("#DeleteConfigRuleButton.Disabled", !canEdit || selected == null);
        cmd.set("#NewConfigRuleButton.Disabled", !canEdit);
        cmd.set("#ResetConfigRulesButton.Disabled", !canEdit);
        cmd.set("#ConfigActionButton.Disabled", !canEdit);
        buildConfigPanel(cmd);
    }

    private void buildConfigPanel(@Nonnull UICommandBuilder cmd) {
        ensureConfigPanelLoaded();
        cmd.set("#ConfigPanelTitle.Text", configSection.label());
        cmd.set("#ConfigPanelSummary.Text", configSection.summary());
        for (ConfigSection section : ConfigSection.values()) {
            cmd.set(section.buttonSelector() + ".Text", section == configSection
                    ? configSectionButtonText(section) + " *"
                    : configSectionButtonText(section));
        }
        String[] labels = configSection.fieldLabels();
        boolean[] toggles = configSection.toggleFields();
        for (int i = 0; i < CONFIG_PANEL_FIELD_COUNT; i++) {
            boolean visible = i < labels.length;
            String row = "#ConfigField" + i + "Row";
            cmd.set(row + ".Visible", visible);
            if (!visible) {
                continue;
            }
            cmd.set("#ConfigField" + i + "Label.Text", labels[i]);
            cmd.set("#ConfigField" + i + "Input.Value", configPanelInputs[i] == null ? "" : configPanelInputs[i]);
            boolean toggle = toggles.length > i && toggles[i];
            boolean cycle = isConfigPanelCycleField(i);
            cmd.set("#ConfigField" + i + "Button.Visible", toggle || cycle);
            cmd.set("#ConfigField" + i + "Button.Text", cycle ? "Cycle" : "Flip");
            cmd.set("#ConfigField" + i + "Button.Disabled", (!toggle && !cycle) || !canManageConfig());
        }
        cmd.set("#SaveConfigPanelButton.Disabled", !canManageConfig());
        cmd.set("#ConfigPanelStatus.Text", canManageConfig()
                ? "Edits save to config files and survive restarts."
                : "Missing permission: hyessentialsx.admin.");
        cmd.set("#ConfigPanelStatus.Style.TextColor", canManageConfig() ? "#8fb6e8" : "#ff7d7d");
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
        cmd.set("#AnnouncementLaunchState.Text", canUse("hyessentialsx.announcement.admin")
                ? "Available: scheduled presets, manual sends, targeting, and actions."
                : "Missing permission: hyessentialsx.announcement.admin");
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
            case "ToggleEscalationSlot0" -> toggleEscalationSlot(0);
            case "ToggleEscalationSlot1" -> toggleEscalationSlot(1);
            case "ToggleEscalationSlot2" -> toggleEscalationSlot(2);
            case "ResetEscalations" -> {
                if (!canManageWarnRules()) {
                    status("Missing permission: hyessentialsx.warnrules or hyessentialsx.admin.", "#ff7d7d");
                    refresh();
                    return;
                }
                context.warningEscalationManager().resetDefaultRules();
                selectedConfigRuleId = null;
                configRuleIdInput = "";
                status("Warning escalation defaults restored.", "#55d98b");
                refresh();
            }
            case "CycleConfigAction" -> {
                configActionInput = nextConfigAction(configActionInput);
                status("Warning rule action: " + configActionInput + ".", "#8fb6e8");
                refresh();
            }
            case "NewConfigRule" -> {
                if (!canManageWarnRules()) {
                    status("Missing permission: hyessentialsx.warnrules or hyessentialsx.admin.", "#ff7d7d");
                    refresh();
                    return;
                }
                newConfigRuleDraft();
                refresh();
            }
            case "SaveConfigRule" -> {
                saveConfigRule();
                refresh();
            }
            case "ToggleConfigRule" -> {
                toggleSelectedConfigRule();
                refresh();
            }
            case "DeleteConfigRule" -> {
                deleteSelectedConfigRule();
                refresh();
            }
            case "ConfigSectionPlayerWarps" -> switchConfigSection(ConfigSection.PLAYER_WARPS);
            case "ConfigSectionEconomy" -> switchConfigSection(ConfigSection.ECONOMY);
            case "ConfigSectionPlaytime" -> switchConfigSection(ConfigSection.PLAYTIME);
            case "ConfigSectionShops" -> switchConfigSection(ConfigSection.SHOPS);
            case "ConfigSectionTravel" -> switchConfigSection(ConfigSection.TRAVEL);
            case "ConfigSectionHomes" -> switchConfigSection(ConfigSection.HOMES);
            case "ConfigSectionChat" -> switchConfigSection(ConfigSection.CHAT);
            case "ConfigSectionScoreboard" -> switchConfigSection(ConfigSection.SCOREBOARD);
            case "SaveConfigPanel" -> {
                saveConfigPanel();
                refresh();
            }
            case "ToggleConfigPanelField0" -> toggleConfigPanelField(0);
            case "ToggleConfigPanelField1" -> toggleConfigPanelField(1);
            case "ToggleConfigPanelField2" -> toggleConfigPanelField(2);
            case "ToggleConfigPanelField3" -> toggleConfigPanelField(3);
            case "ToggleConfigPanelField4" -> toggleConfigPanelField(4);
            case "ToggleConfigPanelField5" -> toggleConfigPanelField(5);
            case "OpenEconomy" -> openEconomy(ref, store);
            case "OpenPlaytime" -> openPlaytime(ref, store);
            case "OpenBans" -> openBans(ref, store);
            case "OpenShops" -> openShops(ref, store);
            case "OpenAuction" -> openAuction(ref, store);
            case "OpenAnnouncements" -> openAnnouncements(ref, store);
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

    private void switchConfigSection(@Nonnull ConfigSection section) {
        configSection = section;
        loadedConfigSection = null;
        ensureConfigPanelLoaded();
        status("Configuration section: " + section.label() + ".", "#8fb6e8");
        refresh();
    }

    private void toggleConfigPanelField(int index) {
        ensureConfigPanelLoaded();
        if (isConfigPanelCycleField(index)) {
            configPanelInputs[index] = switch (configSection) {
                case ECONOMY -> nextEconomyHudAnchor(input(index));
                case SCOREBOARD -> nextScoreboardAnchor(input(index));
                default -> input(index);
            };
            refresh();
            return;
        }
        boolean[] toggles = configSection.toggleFields();
        if (index < 0 || index >= toggles.length || !toggles[index]) {
            return;
        }
        configPanelInputs[index] = String.valueOf(!parseBooleanLoose(configPanelInputs[index], false));
        refresh();
    }

    private boolean isConfigPanelCycleField(int index) {
        return (configSection == ConfigSection.ECONOMY && index == 5)
                || (configSection == ConfigSection.SCOREBOARD && index == 3);
    }

    private void ensureConfigPanelLoaded() {
        if (loadedConfigSection == configSection) {
            return;
        }
        String[] values = configSection.currentValues(this);
        for (int i = 0; i < CONFIG_PANEL_FIELD_COUNT; i++) {
            configPanelInputs[i] = i < values.length ? values[i] : "";
        }
        loadedConfigSection = configSection;
    }

    private void saveConfigPanel() {
        if (!canManageConfig()) {
            status("Missing permission: hyessentialsx.admin.", "#ff7d7d");
            return;
        }
        ensureConfigPanelLoaded();
        try {
            switch (configSection) {
                case PLAYER_WARPS -> {
                    context.config().setPlayerWarpsEnabled(parseBooleanStrict(input(0), "Player warps enabled"));
                    context.config().setPlayerWarpsGuiEnabled(parseBooleanStrict(input(1), "Player warps UI"));
                    context.config().setPlayerWarpAutoApprove(parseBooleanStrict(input(2), "Auto approve"));
                    context.config().setPlayerWarpMaxWarpsPerPlayer(parseInt(input(3), "Max warps per player", 0, 1000));
                    context.config().setPlayerWarpCreateCost(parseMoney(input(4), "Create cost"));
                    context.config().setPlayerWarpVisitCost(parseMoney(input(5), "Visit cost"));
                }
                case ECONOMY -> {
                    context.config().setEconomyStartingBalance(parseMoney(input(0), "Starting balance"));
                    context.config().setEconomyHudEnabled(parseBooleanStrict(input(1), "HUD enabled"));
                    context.config().setEconomyHudDefaultHidden(parseBooleanStrict(input(2), "HUD default hidden"));
                    context.config().setEconomyHudLabel(input(3));
                    context.config().setEconomyHudUpdateIntervalMs(parseInt(input(4), "HUD update ms", 250, 60000));
                    context.config().setEconomyHudAnchor(input(5));
                }
                case PLAYTIME -> {
                    context.config().setPlaytimeRewardsEnabled(parseBooleanStrict(input(0), "Rewards enabled"));
                    context.config().setPlaytimeRewardsAutoClaim(parseBooleanStrict(input(1), "Auto claim"));
                    context.config().setPlaytimeRewardsCheckIntervalSeconds(parseInt(input(2), "Reward check seconds", 5, 86400));
                    context.config().setRankupPlaytimeEnabled(parseBooleanStrict(input(3), "Rankup playtime requirement"));
                    context.config().setRankupCurrencyEnabled(parseBooleanStrict(input(4), "Rankup currency requirement"));
                    context.config().setRankupAutoEnabled(parseBooleanStrict(input(5), "Auto rankup"));
                }
                case SHOPS -> {
                    context.config().setPlayerShopsEnabled(parseBooleanStrict(input(0), "Player shops enabled"));
                    context.config().setPlayerShopMaxShopsPerPlayer(parseInt(input(1), "Max shops per player", 0, 1000));
                    context.config().setPlayerShopCreationCost(parseMoney(input(2), "Shop creation cost"));
                    context.config().setPlayerShopMaxTradeQuantity(parseInt(input(3), "Max trade quantity", 1, 1000000));
                    context.config().setAuctionHouseEnabled(parseBooleanStrict(input(4), "Auction house enabled"));
                    context.config().setAuctionHouseMaxListingsPerPlayer(parseInt(input(5), "Max auction listings", 0, 1000));
                }
                case TRAVEL -> {
                    context.config().setRtpEnabled(parseBooleanStrict(input(0), "RTP enabled"));
                    int minRadius = parseInt(input(1), "Min radius", 0, 1000000);
                    int maxRadius = parseInt(input(2), "Max radius", minRadius, 1000000);
                    context.config().setRtpDistanceRange(minRadius, maxRadius);
                    context.config().setCooldownSeconds(CooldownKeys.RTP, parseInt(input(3), "RTP cooldown", 0, 86400));
                    context.config().setRtpWarmupSeconds(parseInt(input(4), "RTP warmup", 0, 3600));
                    context.config().setPlayerWarpVisitCost(parseMoney(input(5), "Player warp visit cost"));
                }
                case HOMES -> {
                    context.config().setHomesEnabled(parseBooleanStrict(input(0), "Homes enabled"));
                    context.config().setHomeMaxHomesPerPlayer(parseInt(input(1), "Max homes per player", -1, 1000));
                    context.config().setCooldownSeconds(CooldownKeys.HOME, parseInt(input(2), "Home cooldown", 0, 86400));
                    context.config().setHomeWarmupSeconds(parseInt(input(3), "Home warmup", 0, 3600));
                }
                case CHAT -> {
                    context.config().setChatEnabled(parseBooleanStrict(input(0), "Chat format enabled"));
                    context.config().setChatOverrideLuckPerms(parseBooleanStrict(input(1), "Override LuckPerms"));
                    context.config().setAdminChatEnabled(parseBooleanStrict(input(2), "Admin chat enabled"));
                    context.config().setBroadcastEnabled(parseBooleanStrict(input(3), "Broadcast enabled"));
                    context.config().setSleepChatEnabled(parseBooleanStrict(input(4), "Sleep chat messages"));
                    context.config().setCombatLogBlockCommands(parseBooleanStrict(input(5), "Combat command block"));
                }
                case SCOREBOARD -> {
                    context.config().setScoreboardEnabled(parseBooleanStrict(input(0), "Scoreboard enabled"));
                    context.config().setScoreboardDefaultHidden(parseBooleanStrict(input(1), "Default hidden"));
                    context.config().setScoreboardUpdateIntervalMs(parseInt(input(2), "Update interval ms", 250, 60000));
                    context.config().setScoreboardAnchor(input(3));
                    context.config().setScoreboardSize(parseInt(input(4), "Width", 120, 2000), context.config().getScoreboardHeight());
                    context.config().setScoreboardOffsets(parseInt(input(5), "Offset X", 0, 2000), context.config().getScoreboardOffsetY());
                }
            }
            loadedConfigSection = null;
            ensureConfigPanelLoaded();
            if (configSection == ConfigSection.ECONOMY && context.economyHudManager() != null) {
                context.economyHudManager().start();
                context.economyHudManager().refreshAll();
            }
            status(configSection.label() + " saved to config.", "#55d98b");
        } catch (IllegalArgumentException ex) {
            status(ex.getMessage(), "#ff7d7d");
        }
    }

    @Nonnull
    private String input(int index) {
        if (index < 0 || index >= configPanelInputs.length || configPanelInputs[index] == null) {
            return "";
        }
        return configPanelInputs[index].trim();
    }

    private boolean parseBooleanStrict(@Nonnull String raw, @Nonnull String label) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("1")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off") || normalized.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException(label + " must be true or false.");
    }

    private boolean parseBooleanLoose(@Nullable String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("1")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off") || normalized.equals("0")) {
            return false;
        }
        return fallback;
    }

    private int parseInt(@Nonnull String raw, @Nonnull String label, int min, int max) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private long parseMoney(@Nonnull String raw, @Nonnull String label) {
        long parsed = context.economyManager() == null ? parseLongMoney(raw) : context.economyManager().parseAmount(raw);
        if (parsed < 0L) {
            throw new IllegalArgumentException(label + " must be a valid non-negative money amount.");
        }
        return parsed;
    }

    private long parseLongMoney(@Nonnull String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    @Nonnull
    private String moneyText(long amount) {
        return context.economyManager() == null ? String.valueOf(Math.max(0L, amount)) : context.economyManager().formatAmountRaw(amount);
    }

    @Nonnull
    private String configSectionButtonText(@Nonnull ConfigSection section) {
        return switch (section) {
            case PLAYER_WARPS -> "Warps";
            case ECONOMY -> "Economy";
            case PLAYTIME -> "Playtime";
            case SHOPS -> "Shops";
            case TRAVEL -> "RTP";
            case HOMES -> "Homes";
            case CHAT -> "Chat";
            case SCOREBOARD -> "Board";
        };
    }

    @Nonnull
    private String nextEconomyHudAnchor(@Nonnull String anchor) {
        return switch (anchor.trim().toLowerCase(Locale.ROOT)) {
            case "top_left" -> "top_right";
            case "top_right" -> "bottom_right";
            case "bottom_right" -> "bottom_left";
            default -> "top_left";
        };
    }

    @Nonnull
    private String nextScoreboardAnchor(@Nonnull String anchor) {
        return switch (anchor.trim().toLowerCase(Locale.ROOT)) {
            case "top_left" -> "top_right";
            case "top_right" -> "bottom_right";
            case "bottom_right" -> "bottom_left";
            case "bottom_left" -> "top_center";
            case "top_center" -> "bottom_center";
            default -> "top_left";
        };
    }

    private boolean ruleEnabled(@Nonnull String id) {
        for (WarningEscalationRuleModel rule : context.warningEscalationManager().listRules()) {
            if (rule != null && rule.getId().equalsIgnoreCase(id)) {
                return rule.isEnabled();
            }
        }
        return false;
    }

    private void toggleEscalationSlot(int slot) {
        List<WarningEscalationRuleModel> rules = sortedWarningRules();
        if (slot < 0 || slot >= rules.size()) {
            status("No warning escalation rule is assigned to that shortcut.", "#ffb86b");
            refresh();
            return;
        }
        toggleEscalation(rules.get(slot).getId());
    }

    private void toggleEscalation(@Nonnull String id) {
        if (!canManageWarnRules()) {
            status("Missing permission: hyessentialsx.warnrules or hyessentialsx.admin.", "#ff7d7d");
            refresh();
            return;
        }
        boolean next = !ruleEnabled(id);
        context.warningEscalationManager().setRuleEnabled(id, next);
        logActivity("warning-escalation-rule", playerRef.getUuid(), playerRef.getUsername(), id + "=" + next);
        status("Warning escalation rule " + id + " is now " + (next ? "enabled" : "disabled") + ".", "#55d98b");
        refresh();
    }

    @Nonnull
    private List<WarningEscalationRuleModel> sortedWarningRules() {
        List<WarningEscalationRuleModel> rules = new ArrayList<>(context.warningEscalationManager().listRules());
        rules.removeIf(Objects::isNull);
        rules.sort(Comparator.comparingInt(WarningEscalationRuleModel::getThreshold)
                .thenComparing(rule -> rule.getId().toLowerCase(Locale.ROOT)));
        return rules;
    }

    @Nonnull
    private String moderationRuleButtonText(@Nonnull WarningEscalationRuleModel rule) {
        String state = rule.isEnabled() ? "On" : "Off";
        String action = switch (rule.getAction()) {
            case "TEMPBAN" -> "Tempban";
            case "COMMAND" -> "Command";
            default -> rule.getAction().charAt(0) + rule.getAction().substring(1).toLowerCase(Locale.ROOT);
        };
        return rule.getThreshold() + " Warn " + action + ": " + state;
    }

    private void selectConfigRule(@Nonnull String id) {
        WarningEscalationRuleModel rule = context.warningEscalationManager().getRule(id);
        if (rule == null) {
            status("Warning escalation rule not found: " + id, "#ff7d7d");
            return;
        }
        loadConfigRule(rule);
        status("Editing warning escalation rule " + rule.getId() + ".", "#8fb6e8");
    }

    private void loadConfigRule(@Nonnull WarningEscalationRuleModel rule) {
        selectedConfigRuleId = rule.getId();
        configRuleIdInput = rule.getId();
        configRuleNameInput = rule.getName();
        configThresholdInput = String.valueOf(rule.getThreshold());
        configActionInput = rule.getAction();
        configDurationInput = rule.getDurationSeconds() <= 0L ? "0" : compactDuration(rule.getDurationSeconds());
        configWindowInput = rule.getWindowSeconds() <= 0L ? "0" : compactDuration(rule.getWindowSeconds());
        configDetailInput = "COMMAND".equals(rule.getAction()) ? rule.getCommand() : rule.getReason();
    }

    private void newConfigRuleDraft() {
        selectedConfigRuleId = null;
        int next = 1;
        for (WarningEscalationRuleModel rule : context.warningEscalationManager().listRules()) {
            if (rule != null) {
                next = Math.max(next, rule.getThreshold() + 1);
            }
        }
        configRuleIdInput = "warn-" + next + "-mute";
        configRuleNameInput = next + " warnings: temporary mute";
        configThresholdInput = String.valueOf(next);
        configActionInput = "MUTE";
        configDurationInput = "1h";
        configWindowInput = "7d";
        configDetailInput = "Automatic escalation after " + next + " active warnings.";
        status("New warning escalation rule draft created.", "#8fb6e8");
    }

    private void saveConfigRule() {
        if (!canManageWarnRules()) {
            status("Missing permission: hyessentialsx.warnrules or hyessentialsx.admin.", "#ff7d7d");
            return;
        }
        String id = configRuleIdInput.trim();
        if (id.isBlank() || !id.matches("[a-zA-Z0-9_-]{2,48}")) {
            status("Rule IDs must be 2-48 letters, numbers, underscores, or hyphens.", "#ff7d7d");
            return;
        }
        int threshold;
        try {
            threshold = Integer.parseInt(configThresholdInput.trim());
        } catch (NumberFormatException ignored) {
            status("Warnings must be a whole number greater than 0.", "#ff7d7d");
            return;
        }
        if (threshold <= 0) {
            status("Warnings must be greater than 0.", "#ff7d7d");
            return;
        }
        String action = normalizeConfigAction(configActionInput);
        long duration = parseConfigDuration(configDurationInput);
        long window = parseConfigDuration(configWindowInput);
        if (duration < 0L || window < 0L) {
            status("Duration/window must be 0, none, permanent, or a duration like 30m, 2h, 7d, 1w.", "#ff7d7d");
            return;
        }

        WarningEscalationRuleModel existing = selectedConfigRuleId == null
                ? context.warningEscalationManager().getRule(id)
                : context.warningEscalationManager().getRule(selectedConfigRuleId);
        WarningEscalationRuleModel rule = existing == null
                ? new WarningEscalationRuleModel(id, configRuleNameInput, threshold, action, duration, window, "", "")
                : existing;
        rule.setId(id);
        rule.setName(configRuleNameInput.isBlank() ? id : configRuleNameInput);
        rule.setThreshold(threshold);
        rule.setAction(action);
        rule.setDurationSeconds(duration);
        rule.setWindowSeconds(window);
        if ("COMMAND".equals(action)) {
            rule.setCommand(configDetailInput);
            rule.setReason("");
        } else {
            rule.setReason(configDetailInput);
            rule.setCommand("");
        }
        if (existing == null) {
            rule.setEnabled(true);
        }
        if (selectedConfigRuleId != null && !selectedConfigRuleId.equalsIgnoreCase(id)) {
            context.warningEscalationManager().deleteRule(selectedConfigRuleId);
        }
        context.warningEscalationManager().saveRule(rule);
        selectedConfigRuleId = rule.getId();
        loadConfigRule(rule);
        logActivity("warning-escalation-rule-save", playerRef.getUuid(), playerRef.getUsername(),
                rule.getId() + " " + rule.getThreshold() + " " + rule.getAction());
        status("Saved warning escalation rule " + rule.getId() + ".", "#55d98b");
    }

    private void toggleSelectedConfigRule() {
        if (!canManageWarnRules()) {
            status("Missing permission: hyessentialsx.warnrules or hyessentialsx.admin.", "#ff7d7d");
            return;
        }
        WarningEscalationRuleModel rule = selectedConfigRuleId == null ? null : context.warningEscalationManager().getRule(selectedConfigRuleId);
        if (rule == null) {
            status("Select a warning rule first.", "#ffb86b");
            return;
        }
        boolean next = !rule.isEnabled();
        context.warningEscalationManager().setRuleEnabled(rule.getId(), next);
        rule.setEnabled(next);
        loadConfigRule(rule);
        logActivity("warning-escalation-rule", playerRef.getUuid(), playerRef.getUsername(), rule.getId() + "=" + next);
        status("Warning escalation rule " + rule.getId() + " is now " + (next ? "enabled" : "disabled") + ".", "#55d98b");
    }

    private void deleteSelectedConfigRule() {
        if (!canManageWarnRules()) {
            status("Missing permission: hyessentialsx.warnrules or hyessentialsx.admin.", "#ff7d7d");
            return;
        }
        if (selectedConfigRuleId == null || selectedConfigRuleId.isBlank()) {
            status("Select a warning rule first.", "#ffb86b");
            return;
        }
        String id = selectedConfigRuleId;
        if (!context.warningEscalationManager().deleteRule(id)) {
            status("Warning escalation rule not found: " + id, "#ff7d7d");
            return;
        }
        selectedConfigRuleId = null;
        configRuleIdInput = "";
        configRuleNameInput = "";
        configThresholdInput = "3";
        configActionInput = "MUTE";
        configDurationInput = "1h";
        configWindowInput = "7d";
        configDetailInput = "";
        logActivity("warning-escalation-rule-delete", playerRef.getUuid(), playerRef.getUsername(), id);
        status("Deleted warning escalation rule " + id + ".", "#55d98b");
    }

    private boolean canManageWarnRules() {
        return canUse("hyessentialsx.warnrules") || canUse("hyessentialsx.admin");
    }

    private boolean canManageConfig() {
        return canUse("hyessentialsx.admin");
    }

    @Nonnull
    private String normalizeConfigAction(@Nonnull String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MUTE", "TEMPBAN", "BAN", "COMMAND" -> normalized;
            default -> "MUTE";
        };
    }

    @Nonnull
    private String nextConfigAction(@Nonnull String raw) {
        return switch (normalizeConfigAction(raw)) {
            case "MUTE" -> "TEMPBAN";
            case "TEMPBAN" -> "BAN";
            case "BAN" -> "COMMAND";
            default -> "MUTE";
        };
    }

    private long parseConfigDuration(@Nonnull String raw) {
        String trimmed = raw.trim();
        if (trimmed.equals("0") || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("permanent")) {
            return 0L;
        }
        return TimeUtil.parseDurationSeconds(trimmed);
    }

    @Nonnull
    private String compactDuration(long seconds) {
        if (seconds <= 0L) return "0";
        long year = 60L * 60L * 24L * 365L;
        long month = 60L * 60L * 24L * 30L;
        long week = 60L * 60L * 24L * 7L;
        long day = 60L * 60L * 24L;
        long hour = 60L * 60L;
        long minute = 60L;
        if (seconds % year == 0L) return (seconds / year) + "y";
        if (seconds % month == 0L) return (seconds / month) + "mo";
        if (seconds % week == 0L) return (seconds / week) + "w";
        if (seconds % day == 0L) return (seconds / day) + "d";
        if (seconds % hour == 0L) return (seconds / hour) + "h";
        if (seconds % minute == 0L) return (seconds / minute) + "m";
        return seconds + "s";
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
        long expiresAt = warningExpiresAt();
        if (expiresAt < 0L) {
            status("Warning expiry must be 0, none, permanent, or a duration like 30m, 2h, 7d, 1w.", "#ff7d7d");
            return;
        }
        WarningModel warning = new WarningModel(UUID.randomUUID().toString(), name, playerRef.getUsername(), reason,
                System.currentTimeMillis(), expiresAt);
        context.storage().addWarning(id, warning);
        WarningEscalationRuleModel escalated = context.warningEscalationManager().evaluate(id, name, playerRef.getUsername());
        PlayerRef target = Universe.get().getPlayer(id);
        if (target != null) {
            Messages.sendPrefixedKey(target, "warn.target", Map.of("reason", reason));
        }
        logActivity("warn", id, name, reason);
        status(escalated == null ? "Warned " + name + "." : "Warned " + name + " and applied " + escalated.getAction() + ".", "#55d98b");
    }

    private long warningExpiresAt() {
        String raw = warningExpiryInput == null ? "" : warningExpiryInput.trim();
        if (raw.isBlank() || "0".equals(raw) || "none".equalsIgnoreCase(raw) || "permanent".equalsIgnoreCase(raw)) {
            return 0L;
        }
        long seconds = TimeUtil.parseDurationSeconds(raw);
        return seconds < 0L ? -1L : System.currentTimeMillis() + seconds * 1000L;
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

    private void openAnnouncements(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!canUse("hyessentialsx.announcement.admin")) {
            status("Missing permission: hyessentialsx.announcement.admin", "#ff7d7d");
            refresh();
            return;
        }
        Player player = player(ref, store);
        if (player != null) {
            new AnnouncementAdminUI(playerRef, context.autoBroadcastManager()).open(player, ref, store);
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
        String returnWarningExpiry = warningExpiryInput;
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
            page.warningExpiryInput = returnWarningExpiry;
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
            case "config", "configuration" -> Tab.CONFIG;
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
        CONFIG,
        SYSTEMS,
        LAUNCH
    }

    private enum ConfigSection {
        PLAYER_WARPS("Player Warps", "#ConfigSectionWarpsButton",
                "Limits, approval behavior, and economy costs for player-created warps.",
                new String[]{"Enabled", "GUI Enabled", "Auto Approve", "Max Warps", "Create Cost", "Visit Cost"},
                new boolean[]{true, true, true, false, false, false}) {
            @Override
            String[] currentValues(HyEssentialsXDashboardUI ui) {
                return new String[]{
                        String.valueOf(ui.context.config().isPlayerWarpsEnabled()),
                        String.valueOf(ui.context.config().isPlayerWarpsGuiEnabled()),
                        String.valueOf(ui.context.config().isPlayerWarpAutoApprove()),
                        String.valueOf(ui.context.config().getPlayerWarpMaxWarpsPerPlayer()),
                        ui.moneyText(ui.context.config().getPlayerWarpCreateCost()),
                        ui.moneyText(ui.context.config().getPlayerWarpVisitCost())
                };
            }
        },
        ECONOMY("Economy HUD", "#ConfigSectionEconomyButton",
                "Starting balance and HUD defaults used by the economy display.",
                new String[]{"Starting Balance", "HUD Enabled", "HUD Hidden", "HUD Label", "Update MS", "Anchor"},
                new boolean[]{false, true, true, false, false, false}) {
            @Override
            String[] currentValues(HyEssentialsXDashboardUI ui) {
                return new String[]{
                        ui.moneyText(ui.context.config().getEconomyStartingBalance()),
                        String.valueOf(ui.context.config().isEconomyHudEnabled()),
                        String.valueOf(ui.context.config().isEconomyHudDefaultHidden()),
                        ui.context.config().getEconomyHudLabel(),
                        String.valueOf(ui.context.config().getEconomyHudUpdateIntervalMs()),
                        ui.context.config().getEconomyHudAnchor()
                };
            }
        },
        PLAYTIME("Playtime & Rankups", "#ConfigSectionPlaytimeButton",
                "Reward automation and rankup requirement switches.",
                new String[]{"Rewards Enabled", "Auto Claim", "Check Seconds", "Use Playtime", "Use Currency", "Auto Rankup"},
                new boolean[]{true, true, false, true, true, true}) {
            @Override
            String[] currentValues(HyEssentialsXDashboardUI ui) {
                return new String[]{
                        String.valueOf(ui.context.config().isPlaytimeRewardsEnabled()),
                        String.valueOf(ui.context.config().isPlaytimeRewardsAutoClaim()),
                        String.valueOf(ui.context.config().getPlaytimeRewardsCheckIntervalSeconds()),
                        String.valueOf(ui.context.config().isRankupPlaytimeEnabled()),
                        String.valueOf(ui.context.config().isRankupCurrencyEnabled()),
                        String.valueOf(ui.context.config().isRankupAutoEnabled())
                };
            }
        },
        SHOPS("Shops & Auction", "#ConfigSectionShopsButton",
                "Player shop limits, creation cost, and auction listing caps.",
                new String[]{"Player Shops", "Max Shops", "Shop Cost", "Max Trade Qty", "Auction House", "Max Listings"},
                new boolean[]{true, false, false, false, true, false}) {
            @Override
            String[] currentValues(HyEssentialsXDashboardUI ui) {
                return new String[]{
                        String.valueOf(ui.context.config().isPlayerShopsEnabled()),
                        String.valueOf(ui.context.config().getPlayerShopMaxShopsPerPlayer()),
                        ui.moneyText(ui.context.config().getPlayerShopCreationCost()),
                        String.valueOf(ui.context.config().getPlayerShopMaxTradeQuantity()),
                        String.valueOf(ui.context.config().isAuctionHouseEnabled()),
                        String.valueOf(ui.context.config().getAuctionHouseMaxListingsPerPlayer())
                };
            }
        },
        TRAVEL("RTP & Travel", "#ConfigSectionTravelButton",
                "Random teleport safety radius, cooldown, warmup, and player-warp visit cost.",
                new String[]{"RTP Enabled", "Min Radius", "Max Radius", "Cooldown Sec", "Warmup Sec", "Warp Visit Cost"},
                new boolean[]{true, false, false, false, false, false}) {
            @Override
            String[] currentValues(HyEssentialsXDashboardUI ui) {
                return new String[]{
                        String.valueOf(ui.context.config().isRtpEnabled()),
                        String.valueOf(ui.context.config().getRtpMinDistance()),
                        String.valueOf(ui.context.config().getRtpMaxDistance()),
                        String.valueOf(ui.context.config().getCooldownSeconds(CooldownKeys.RTP)),
                        String.valueOf(ui.context.config().getRtpWarmupSeconds()),
                        ui.moneyText(ui.context.config().getPlayerWarpVisitCost())
                };
            }
        },
        HOMES("Homes", "#ConfigSectionHomesButton",
                "Default home limits plus cooldown and warmup timing. -1 means unlimited.",
                new String[]{"Homes Enabled", "Max Homes", "Cooldown Sec", "Warmup Sec"},
                new boolean[]{true, false, false, false}) {
            @Override
            String[] currentValues(HyEssentialsXDashboardUI ui) {
                return new String[]{
                        String.valueOf(ui.context.config().isHomesEnabled()),
                        String.valueOf(ui.context.config().getHomeMaxHomesPerPlayer()),
                        String.valueOf(ui.context.config().getCooldownSeconds(CooldownKeys.HOME)),
                        String.valueOf(ui.context.config().getHomeWarmupSeconds())
                };
            }
        },
        CHAT("Chat Moderation", "#ConfigSectionChatButton",
                "Chat format, admin chat, broadcast, sleep chat, and combat command protection.",
                new String[]{"Chat Format", "Override LP", "Admin Chat", "Broadcast", "Sleep Chat", "Combat Blocks Cmds"},
                new boolean[]{true, true, true, true, true, true}) {
            @Override
            String[] currentValues(HyEssentialsXDashboardUI ui) {
                return new String[]{
                        String.valueOf(ui.context.config().isChatEnabled()),
                        String.valueOf(ui.context.config().isOverrideLuckPermsChatFormat()),
                        String.valueOf(ui.context.config().isAdminChatEnabled()),
                        String.valueOf(ui.context.config().isBroadcastEnabled()),
                        String.valueOf(ui.context.config().isSleepChatEnabled()),
                        String.valueOf(ui.context.config().isCombatLogBlockCommands())
                };
            }
        },
        SCOREBOARD("Scoreboard", "#ConfigSectionScoreboardButton",
                "Preset-style scoreboard placement and refresh controls.",
                new String[]{"Enabled", "Default Hidden", "Update MS", "Anchor", "Width", "Offset X"},
                new boolean[]{true, true, false, false, false, false}) {
            @Override
            String[] currentValues(HyEssentialsXDashboardUI ui) {
                return new String[]{
                        String.valueOf(ui.context.config().isScoreboardEnabled()),
                        String.valueOf(ui.context.config().isScoreboardDefaultHidden()),
                        String.valueOf(ui.context.config().getScoreboardUpdateIntervalMs()),
                        ui.context.config().getScoreboardAnchor(),
                        String.valueOf(ui.context.config().getScoreboardWidth()),
                        String.valueOf(ui.context.config().getScoreboardOffsetX())
                };
            }
        };

        private final String label;
        private final String buttonSelector;
        private final String summary;
        private final String[] fieldLabels;
        private final boolean[] toggleFields;

        ConfigSection(String label,
                      String buttonSelector,
                      String summary,
                      String[] fieldLabels,
                      boolean[] toggleFields) {
            this.label = label;
            this.buttonSelector = buttonSelector;
            this.summary = summary;
            this.fieldLabels = fieldLabels;
            this.toggleFields = toggleFields;
        }

        @Nonnull
        String label() {
            return label;
        }

        @Nonnull
        String buttonSelector() {
            return buttonSelector;
        }

        @Nonnull
        String summary() {
            return summary;
        }

        @Nonnull
        String[] fieldLabels() {
            return fieldLabels;
        }

        @Nonnull
        boolean[] toggleFields() {
            return toggleFields;
        }

        abstract String[] currentValues(HyEssentialsXDashboardUI ui);
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
                .append(new KeyedCodec<>("ConfigRuleAction", Codec.STRING), (d, v) -> d.configRuleAction = v, d -> d.configRuleAction).add()
                .append(new KeyedCodec<>("ConfigRuleId", Codec.STRING), (d, v) -> d.configRuleId = v, d -> d.configRuleId).add()
                .append(new KeyedCodec<>("@PlayerSearch", Codec.STRING), (d, v) -> d.playerSearch = v, d -> d.playerSearch).add()
                .append(new KeyedCodec<>("@ActionReason", Codec.STRING), (d, v) -> d.actionReason = v, d -> d.actionReason).add()
                .append(new KeyedCodec<>("@WarningExpiry", Codec.STRING), (d, v) -> d.warningExpiry = v, d -> d.warningExpiry).add()
                .append(new KeyedCodec<>("@ConfigRuleIdInput", Codec.STRING), (d, v) -> d.configRuleIdInput = v, d -> d.configRuleIdInput).add()
                .append(new KeyedCodec<>("@ConfigRuleNameInput", Codec.STRING), (d, v) -> d.configRuleNameInput = v, d -> d.configRuleNameInput).add()
                .append(new KeyedCodec<>("@ConfigThresholdInput", Codec.STRING), (d, v) -> d.configThresholdInput = v, d -> d.configThresholdInput).add()
                .append(new KeyedCodec<>("@ConfigDurationInput", Codec.STRING), (d, v) -> d.configDurationInput = v, d -> d.configDurationInput).add()
                .append(new KeyedCodec<>("@ConfigWindowInput", Codec.STRING), (d, v) -> d.configWindowInput = v, d -> d.configWindowInput).add()
                .append(new KeyedCodec<>("@ConfigDetailInput", Codec.STRING), (d, v) -> d.configDetailInput = v, d -> d.configDetailInput).add()
                .append(new KeyedCodec<>("@ConfigPanelInput0", Codec.STRING), (d, v) -> d.configPanelInput0 = v, d -> d.configPanelInput0).add()
                .append(new KeyedCodec<>("@ConfigPanelInput1", Codec.STRING), (d, v) -> d.configPanelInput1 = v, d -> d.configPanelInput1).add()
                .append(new KeyedCodec<>("@ConfigPanelInput2", Codec.STRING), (d, v) -> d.configPanelInput2 = v, d -> d.configPanelInput2).add()
                .append(new KeyedCodec<>("@ConfigPanelInput3", Codec.STRING), (d, v) -> d.configPanelInput3 = v, d -> d.configPanelInput3).add()
                .append(new KeyedCodec<>("@ConfigPanelInput4", Codec.STRING), (d, v) -> d.configPanelInput4 = v, d -> d.configPanelInput4).add()
                .append(new KeyedCodec<>("@ConfigPanelInput5", Codec.STRING), (d, v) -> d.configPanelInput5 = v, d -> d.configPanelInput5).add()
                .build();

        private String tab;
        private String profileTab;
        private String action;
        private String playerAction;
        private String playerUuid;
        private String playerName;
        private String configRuleAction;
        private String configRuleId;
        private String playerSearch;
        private String actionReason;
        private String warningExpiry;
        private String configRuleIdInput;
        private String configRuleNameInput;
        private String configThresholdInput;
        private String configDurationInput;
        private String configWindowInput;
        private String configDetailInput;
        private String configPanelInput0;
        private String configPanelInput1;
        private String configPanelInput2;
        private String configPanelInput3;
        private String configPanelInput4;
        private String configPanelInput5;

        @Nullable
        private String configPanelInput(int index) {
            return switch (index) {
                case 0 -> configPanelInput0;
                case 1 -> configPanelInput1;
                case 2 -> configPanelInput2;
                case 3 -> configPanelInput3;
                case 4 -> configPanelInput4;
                case 5 -> configPanelInput5;
                default -> null;
            };
        }
    }
}
