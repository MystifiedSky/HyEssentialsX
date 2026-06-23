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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeRewardManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.RankupManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlaytimeRewardModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.UiBackTarget;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PlaytimeAdminUI extends InteractiveCustomUIPage<PlaytimeAdminUI.AdminEventData> {

    private static final String LAYOUT = "hyessentialsx/PlaytimeAdminPage.ui";
    private static final String REWARD_ROW_LAYOUT = "hyessentialsx/PlaytimeAdminRewardRow.ui";
    private static final String TOP_ROW_LAYOUT = "hyessentialsx/PlaytimeAdminTopRow.ui";
    private static final int REWARD_PAGE_SIZE = 8;

    private final PlayerRef playerRef;
    private final PlaytimeManager playtime;
    private final PlaytimeRewardManager rewards;
    private final RankupManager rankups;
    private final StorageManager storage;
    private final ConfigManager config;
    @Nullable
    private final UiBackTarget backTarget;

    private Tab currentTab = Tab.DASHBOARD;
    private String rewardSearch = "";
    private int rewardPage = 0;
    @Nullable
    private String selectedRewardId;

    private String draftIdInput = "";
    private String draftRequiredInput = "1h";
    private String draftCostInput = "0";
    private String draftRankInput = "";
    private boolean draftAutoClaim = true;
    private String draftCommandsInput = "";
    private String draftBroadcastInput = "";

    private boolean cfgGuiEnabled = true;
    private boolean cfgRewardsEnabled = true;
    private boolean cfgAutoClaimEnabled = true;
    private boolean cfgRankupPlaytimeEnabled = true;
    private boolean cfgRankupCurrencyEnabled = true;
    private boolean cfgRankupAutoEnabled = false;
    private boolean cfgRankupAutoUseCurrency = false;
    private String cfgTopLimitInput = "100";
    private String cfgCheckIntervalInput = "30";
    private String cfgRankupAutoCheckInput = "60";

    private String statusMessage = "";
    private String statusColor = "#89a6cb";

    public PlaytimeAdminUI(@Nonnull PlayerRef playerRef,
                           @Nonnull PlaytimeManager playtime,
                           @Nonnull PlaytimeRewardManager rewards,
                           @Nonnull RankupManager rankups,
                           @Nonnull StorageManager storage,
                           @Nonnull ConfigManager config) {
        this(playerRef, playtime, rewards, rankups, storage, config, null);
    }

    public PlaytimeAdminUI(@Nonnull PlayerRef playerRef,
                           @Nonnull PlaytimeManager playtime,
                           @Nonnull PlaytimeRewardManager rewards,
                           @Nonnull RankupManager rankups,
                           @Nonnull StorageManager storage,
                           @Nonnull ConfigManager config,
                           @Nullable UiBackTarget backTarget) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminEventData.CODEC);
        this.playerRef = playerRef;
        this.playtime = playtime;
        this.rewards = rewards;
        this.rankups = rankups;
        this.storage = storage;
        this.config = config;
        this.backTarget = backTarget;
        syncConfigDraftFromConfig();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        bindEvents(events);
        rebuild(cmd, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull AdminEventData data) {
        if (data.tab != null) {
            currentTab = parseTab(data.tab);
            refresh();
            return;
        }
        if (data.rewardSearch != null) {
            String normalized = data.rewardSearch.trim().toLowerCase(Locale.ROOT);
            if (!rewardSearch.equals(normalized)) {
                rewardPage = 0;
            }
            rewardSearch = normalized;
            refresh();
            return;
        }
        if ("Select".equalsIgnoreCase(data.rewardAction) && data.rewardId != null) {
            selectedRewardId = data.rewardId.trim();
            PlaytimeRewardModel selected = findRewardById(selectedRewardId);
            if (selected != null) {
                loadDraftFromReward(selected);
            }
            refresh();
            return;
        }

        if (data.draftId != null) {
            draftIdInput = data.draftId;
        }
        if (data.draftRequired != null) {
            draftRequiredInput = data.draftRequired;
        }
        if (data.draftCost != null) {
            draftCostInput = data.draftCost;
        }
        if (data.draftRank != null) {
            draftRankInput = data.draftRank;
        }
        if (data.draftCommands != null) {
            draftCommandsInput = data.draftCommands;
        }
        if (data.draftBroadcast != null) {
            draftBroadcastInput = data.draftBroadcast;
        }
        if (data.cfgTopLimit != null) {
            cfgTopLimitInput = data.cfgTopLimit;
        }
        if (data.cfgCheckInterval != null) {
            cfgCheckIntervalInput = data.cfgCheckInterval;
        }
        if (data.cfgRankupAutoCheck != null) {
            cfgRankupAutoCheckInput = data.cfgRankupAutoCheck;
        }

        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "Close" -> close();
            case "Back" -> openBack(ref, store);
            case "PrevRewardPage" -> {
                if (rewardPage > 0) {
                    rewardPage--;
                }
                refresh();
            }
            case "NextRewardPage" -> {
                rewardPage++;
                refresh();
            }
            case "SaveReward" -> {
                saveRewardFromDraft();
                refresh();
            }
            case "DeleteReward" -> {
                deleteSelectedReward();
                refresh();
            }
            case "ClearDraft" -> {
                clearDraft();
                setStatus("Reward draft cleared.", "#89a6cb");
                refresh();
            }
            case "ToggleDraftAutoClaim" -> {
                draftAutoClaim = !draftAutoClaim;
                refresh();
            }
            case "ToggleGui" -> {
                cfgGuiEnabled = !cfgGuiEnabled;
                refresh();
            }
            case "ToggleRewards" -> {
                cfgRewardsEnabled = !cfgRewardsEnabled;
                refresh();
            }
            case "ToggleAutoClaim" -> {
                cfgAutoClaimEnabled = !cfgAutoClaimEnabled;
                refresh();
            }
            case "ToggleRankupPlaytime" -> {
                cfgRankupPlaytimeEnabled = !cfgRankupPlaytimeEnabled;
                refresh();
            }
            case "ToggleRankupCurrency" -> {
                cfgRankupCurrencyEnabled = !cfgRankupCurrencyEnabled;
                refresh();
            }
            case "ToggleRankupAuto" -> {
                cfgRankupAutoEnabled = !cfgRankupAutoEnabled;
                refresh();
            }
            case "ToggleRankupAutoUseCurrency" -> {
                cfgRankupAutoUseCurrency = !cfgRankupAutoUseCurrency;
                refresh();
            }
            case "SaveConfig" -> {
                applyConfigInputs();
                refresh();
            }
            case "ReloadConfig" -> {
                config.reload();
                syncConfigDraftFromConfig();
                restartRuntimeManagers();
                setStatus("Playtime config reloaded.", "#55ff55");
                refresh();
            }
            default -> {
            }
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        bindEvents(events);
        rebuild(cmd, events);
        sendUpdate(cmd, events, false);
    }

    private void bindEvents(@Nonnull UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", "Close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackToParentButton",
                EventData.of("Action", "Back"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabDashboard",
                EventData.of("Tab", "Dashboard"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabRewards",
                EventData.of("Tab", "Rewards"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabTop",
                EventData.of("Tab", "Top"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabConfig",
                EventData.of("Tab", "Config"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RewardSearch",
                EventData.of("@RewardSearch", "#RewardSearch.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RewardPrevPageButton",
                EventData.of("Action", "PrevRewardPage"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RewardNextPageButton",
                EventData.of("Action", "NextRewardPage"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DraftIdInput",
                EventData.of("@DraftId", "#DraftIdInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DraftRequiredInput",
                EventData.of("@DraftRequired", "#DraftRequiredInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DraftCostInput",
                EventData.of("@DraftCost", "#DraftCostInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DraftRankInput",
                EventData.of("@DraftRank", "#DraftRankInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DraftCommandsInput",
                EventData.of("@DraftCommands", "#DraftCommandsInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DraftBroadcastInput",
                EventData.of("@DraftBroadcast", "#DraftBroadcastInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DraftAutoClaimToggle",
                EventData.of("Action", "ToggleDraftAutoClaim"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveRewardButton",
                EventData.of("Action", "SaveReward"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteRewardButton",
                EventData.of("Action", "DeleteReward"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearDraftButton",
                EventData.of("Action", "ClearDraft"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#CfgGuiToggle",
                EventData.of("Action", "ToggleGui"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CfgRewardsToggle",
                EventData.of("Action", "ToggleRewards"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CfgAutoClaimToggle",
                EventData.of("Action", "ToggleAutoClaim"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CfgRankupPlaytimeToggle",
                EventData.of("Action", "ToggleRankupPlaytime"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CfgRankupCurrencyToggle",
                EventData.of("Action", "ToggleRankupCurrency"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CfgRankupAutoToggle",
                EventData.of("Action", "ToggleRankupAuto"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CfgRankupAutoUseCurrencyToggle",
                EventData.of("Action", "ToggleRankupAutoUseCurrency"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CfgTopLimitInput",
                EventData.of("@CfgTopLimit", "#CfgTopLimitInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CfgCheckIntervalInput",
                EventData.of("@CfgCheckInterval", "#CfgCheckIntervalInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CfgRankupAutoCheckInput",
                EventData.of("@CfgRankupAutoCheck", "#CfgRankupAutoCheckInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveConfigButton",
                EventData.of("Action", "SaveConfig"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadConfigButton",
                EventData.of("Action", "ReloadConfig"), false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.set("#DashboardContent.Visible", currentTab == Tab.DASHBOARD);
        cmd.set("#BackToParentButton.Visible", backTarget != null);
        cmd.set("#RewardsContent.Visible", currentTab == Tab.REWARDS);
        cmd.set("#TopContent.Visible", currentTab == Tab.TOP);
        cmd.set("#ConfigContent.Visible", currentTab == Tab.CONFIG);

        cmd.set("#TabDashboard.Text", currentTab == Tab.DASHBOARD ? "DASHBOARD *" : "DASHBOARD");
        cmd.set("#TabRewards.Text", currentTab == Tab.REWARDS ? "REWARDS *" : "REWARDS");
        cmd.set("#TabTop.Text", currentTab == Tab.TOP ? "TOP *" : "TOP");
        cmd.set("#TabConfig.Text", currentTab == Tab.CONFIG ? "CONFIG *" : "CONFIG");

        buildDashboardTab(cmd);
        buildRewardsTab(cmd, events);
        buildTopTab(cmd);
        buildConfigTab(cmd);
    }

    private void openBack(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (backTarget == null) {
            close();
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        backTarget.open(player, ref, store);
    }

    private void buildDashboardTab(@Nonnull UICommandBuilder cmd) {
        List<PlaytimeEntry> entries = collectTopEntries();
        long totalSeconds = 0L;
        for (PlaytimeEntry entry : entries) {
            if (Long.MAX_VALUE - totalSeconds < entry.seconds) {
                totalSeconds = Long.MAX_VALUE;
                break;
            }
            totalSeconds += entry.seconds;
        }
        long averageSeconds = entries.isEmpty() ? 0L : totalSeconds / entries.size();
        int rewardCount = config.getPlaytimeRewards().size();

        cmd.set("#TrackedPlayersValue.Text", String.valueOf(entries.size()));
        cmd.set("#TotalPlaytimeValue.Text", TimeUtil.formatDurationSeconds(totalSeconds));
        cmd.set("#AveragePlaytimeValue.Text", TimeUtil.formatDurationSeconds(averageSeconds));
        cmd.set("#RewardCountValue.Text", String.valueOf(rewardCount));

        String hint = entries.isEmpty()
                ? "No playtime records found yet."
                : ("Top Player: " + entries.get(0).name + " (" + TimeUtil.formatDurationSeconds(entries.get(0).seconds) + ")");
        cmd.set("#DashboardHint.Text", hint);
    }

    private void buildRewardsTab(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.set("#RewardSearch.Value", rewardSearch);
        cmd.set("#DraftIdInput.Value", draftIdInput);
        cmd.set("#DraftRequiredInput.Value", draftRequiredInput);
        cmd.set("#DraftCostInput.Value", draftCostInput);
        cmd.set("#DraftRankInput.Value", draftRankInput);
        cmd.set("#DraftCommandsInput.Value", draftCommandsInput);
        cmd.set("#DraftBroadcastInput.Value", draftBroadcastInput);
        cmd.set("#DraftAutoClaimToggle.Text", draftAutoClaim ? "ON" : "OFF");

        List<PlaytimeRewardModel> filtered = collectFilteredRewards(rewardSearch);
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) REWARD_PAGE_SIZE));
        if (rewardPage >= totalPages) {
            rewardPage = totalPages - 1;
        }
        int start = rewardPage * REWARD_PAGE_SIZE;
        int end = Math.min(filtered.size(), start + REWARD_PAGE_SIZE);

        cmd.clear("#RewardList");
        for (int i = start; i < end; i++) {
            PlaytimeRewardModel reward = filtered.get(i);
            int displayIndex = i - start;
            cmd.append("#RewardList", REWARD_ROW_LAYOUT);
            String row = "#RewardList[" + displayIndex + "]";
            cmd.set(row + " #RewardId.Text", reward.getId());
            cmd.set(row + " #RewardRequired.Text", TimeUtil.formatDurationSeconds(reward.getRequiredSeconds()));
            String details = reward.getCommands().isEmpty()
                    ? "No commands"
                    : reward.getCommands().size() + " command(s)";
            if (!reward.getRank().isBlank()) {
                details = "Rank: " + reward.getRank();
            } else if (reward.getRequiredCost() > 0L) {
                details = "Cost: " + reward.getRequiredCost();
            }
            cmd.set(row + " #RewardCommands.Text", details);
            cmd.set(row + " #SelectionIndicator.Visible", reward.getId().equalsIgnoreCase(selectedRewardId));
            events.addEventBinding(CustomUIEventBindingType.Activating, row,
                    EventData.of("RewardAction", "Select").append("RewardId", reward.getId()), false);
        }
        if (start >= end) {
            cmd.appendInline("#RewardList",
                    "Label { Text: \"No rewards found\"; Style: (FontSize: 12, TextColor: #8da8cc); }");
        }

        cmd.set("#RewardPageInfo.Text", "Page " + (rewardPage + 1) + "/" + totalPages);
        if (selectedRewardId == null || selectedRewardId.isBlank()) {
            cmd.set("#SelectedRewardLabel.Text", "Selected: none");
        } else {
            cmd.set("#SelectedRewardLabel.Text", "Selected: " + selectedRewardId);
        }
    }

    private void buildTopTab(@Nonnull UICommandBuilder cmd) {
        List<PlaytimeEntry> top = collectTopEntries();
        int limit = Math.min(top.size(), Math.max(10, config.getPlaytimeTopLimit()));

        cmd.clear("#TopList");
        for (int i = 0; i < limit; i++) {
            PlaytimeEntry entry = top.get(i);
            cmd.append("#TopList", TOP_ROW_LAYOUT);
            String row = "#TopList[" + i + "]";
            cmd.set(row + " #Rank.Text", "#" + (i + 1));
            cmd.set(row + " #PlayerName.Text", entry.name);
            cmd.set(row + " #Playtime.Text", TimeUtil.formatDurationSeconds(entry.seconds));
        }

        if (limit == 0) {
            cmd.appendInline("#TopList",
                    "Label { Text: \"No playtime entries found\"; Style: (FontSize: 12, TextColor: #8da8cc); }");
        }
        cmd.set("#TopInfo.Text", "Showing " + limit + " of " + top.size() + " tracked players");
    }

    private void buildConfigTab(@Nonnull UICommandBuilder cmd) {
        cmd.set("#CfgGuiToggle.Text", cfgGuiEnabled ? "ON" : "OFF");
        cmd.set("#CfgRewardsToggle.Text", cfgRewardsEnabled ? "ON" : "OFF");
        cmd.set("#CfgAutoClaimToggle.Text", cfgAutoClaimEnabled ? "ON" : "OFF");
        cmd.set("#CfgRankupPlaytimeToggle.Text", cfgRankupPlaytimeEnabled ? "ON" : "OFF");
        cmd.set("#CfgRankupCurrencyToggle.Text", cfgRankupCurrencyEnabled ? "ON" : "OFF");
        cmd.set("#CfgRankupAutoToggle.Text", cfgRankupAutoEnabled ? "ON" : "OFF");
        cmd.set("#CfgRankupAutoUseCurrencyToggle.Text", cfgRankupAutoUseCurrency ? "ON" : "OFF");
        cmd.set("#CfgTopLimitInput.Value", cfgTopLimitInput);
        cmd.set("#CfgCheckIntervalInput.Value", cfgCheckIntervalInput);
        cmd.set("#CfgRankupAutoCheckInput.Value", cfgRankupAutoCheckInput);
        cmd.set("#ConfigStatus.Text", statusMessage);
        cmd.set("#ConfigStatus.Style.TextColor", statusColor);
    }

    private void saveRewardFromDraft() {
        String id = draftIdInput == null ? "" : draftIdInput.trim();
        if (id.isBlank()) {
            setStatus("Reward ID is required.", "#ff6666");
            return;
        }

        long requiredSeconds = parseDurationInput(draftRequiredInput);
        if (requiredSeconds < 0L) {
            setStatus("Required time is invalid. Use values like 30m, 2h, 1d.", "#ff6666");
            return;
        }
        Long requiredCost = parseOptionalNonNegativeLong(draftCostInput);
        if (requiredCost == null) {
            setStatus("Required cost must be a whole number 0 or higher.", "#ff6666");
            return;
        }
        String rank = draftRankInput == null ? "" : draftRankInput.trim();

        List<String> commands = parseCommands(draftCommandsInput);
        String broadcast = draftBroadcastInput == null ? "" : draftBroadcastInput.trim();
        if (commands.isEmpty() && broadcast.isBlank()) {
            setStatus("Add at least one command or broadcast message.", "#ff6666");
            return;
        }

        List<PlaytimeRewardModel> updated = new ArrayList<>(config.getPlaytimeRewards());
        int existingIndex = indexOfReward(updated, id);
        PlaytimeRewardModel newReward = new PlaytimeRewardModel(
                id,
                requiredSeconds,
                requiredCost,
                rank,
                draftAutoClaim,
                commands,
                broadcast
        );
        if (existingIndex >= 0) {
            updated.set(existingIndex, newReward);
            setStatus("Updated reward '" + id + "'.", "#55ff55");
        } else {
            updated.add(newReward);
            setStatus("Added reward '" + id + "'.", "#55ff55");
        }
        config.setPlaytimeRewards(updated);
        restartRuntimeManagers();
        selectedRewardId = id;
        loadDraftFromReward(newReward);
    }

    private void deleteSelectedReward() {
        if (selectedRewardId == null || selectedRewardId.isBlank()) {
            setStatus("Select a reward first.", "#ff6666");
            return;
        }

        List<PlaytimeRewardModel> current = new ArrayList<>(config.getPlaytimeRewards());
        int index = indexOfReward(current, selectedRewardId);
        if (index < 0) {
            setStatus("Selected reward no longer exists.", "#ff6666");
            return;
        }

        String removedId = current.get(index).getId();
        current.remove(index);
        config.setPlaytimeRewards(current);
        restartRuntimeManagers();
        selectedRewardId = null;
        clearDraft();
        setStatus("Removed reward '" + removedId + "'.", "#55ff55");
    }

    private void clearDraft() {
        draftIdInput = "";
        draftRequiredInput = "1h";
        draftCostInput = "0";
        draftRankInput = "";
        draftAutoClaim = true;
        draftCommandsInput = "";
        draftBroadcastInput = "";
    }

    private void applyConfigInputs() {
        Integer topLimit = parsePositiveInt(cfgTopLimitInput);
        Integer checkInterval = parsePositiveInt(cfgCheckIntervalInput);
        Integer rankupAutoCheck = parsePositiveInt(cfgRankupAutoCheckInput);
        if (topLimit == null) {
            setStatus("Top limit must be a whole number above 0.", "#ff6666");
            return;
        }
        if (checkInterval == null) {
            setStatus("Reward check interval must be a whole number above 0.", "#ff6666");
            return;
        }
        if (rankupAutoCheck == null) {
            setStatus("Auto rankup check interval must be a whole number above 0.", "#ff6666");
            return;
        }

        config.setPlaytimeGuiEnabled(cfgGuiEnabled);
        config.setPlaytimeRewardsEnabled(cfgRewardsEnabled);
        config.setPlaytimeRewardsAutoClaim(cfgAutoClaimEnabled);
        config.setPlaytimeTopLimit(topLimit);
        config.setPlaytimeRewardsCheckIntervalSeconds(checkInterval);
        config.setRankupPlaytimeEnabled(cfgRankupPlaytimeEnabled);
        config.setRankupCurrencyEnabled(cfgRankupCurrencyEnabled);
        config.setRankupAutoEnabled(cfgRankupAutoEnabled);
        config.setRankupAutoUseCurrency(cfgRankupAutoUseCurrency);
        config.setRankupAutoCheckSeconds(rankupAutoCheck);
        restartRuntimeManagers();
        syncConfigDraftFromConfig();
        setStatus("Playtime config saved.", "#55ff55");
    }

    private void syncConfigDraftFromConfig() {
        cfgGuiEnabled = config.isPlaytimeGuiEnabled();
        cfgRewardsEnabled = config.isPlaytimeRewardsEnabled();
        cfgAutoClaimEnabled = config.isPlaytimeRewardsAutoClaim();
        cfgRankupPlaytimeEnabled = config.isRankupPlaytimeEnabled();
        cfgRankupCurrencyEnabled = config.isRankupCurrencyEnabled();
        cfgRankupAutoEnabled = config.isRankupAutoEnabled();
        cfgRankupAutoUseCurrency = config.isRankupAutoUseCurrency();
        cfgTopLimitInput = String.valueOf(config.getPlaytimeTopLimit());
        cfgCheckIntervalInput = String.valueOf(config.getPlaytimeRewardsCheckIntervalSeconds());
        cfgRankupAutoCheckInput = String.valueOf(config.getRankupAutoCheckSeconds());
    }

    private void restartRuntimeManagers() {
        rewards.start();
        rankups.shutdown();
        rankups.start();
    }

    @Nonnull
    private List<PlaytimeRewardModel> collectFilteredRewards(@Nonnull String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<PlaytimeRewardModel> out = new ArrayList<>();
        for (PlaytimeRewardModel reward : config.getPlaytimeRewards()) {
            if (reward == null) {
                continue;
            }
            String id = reward.getId();
            if (!normalized.isBlank() && !id.toLowerCase(Locale.ROOT).contains(normalized)) {
                continue;
            }
            out.add(reward);
        }
        out.sort(Comparator.comparingLong(PlaytimeRewardModel::getRequiredSeconds)
                .thenComparing(r -> r.getId().toLowerCase(Locale.ROOT)));
        return out;
    }

    @Nonnull
    private List<PlaytimeEntry> collectTopEntries() {
        Set<UUID> ids = new HashSet<>(storage.listPlayerIds());
        Universe universe = Universe.get();
        if (universe != null) {
            for (PlayerRef online : universe.getPlayers()) {
                if (online != null) {
                    ids.add(online.getUuid());
                }
            }
        }

        List<PlaytimeEntry> out = new ArrayList<>();
        for (UUID id : ids) {
            long seconds = playtime.getPlaytimeSeconds(id);
            out.add(new PlaytimeEntry(id, resolveDisplayName(id), seconds));
        }
        out.sort(Comparator.comparingLong((PlaytimeEntry row) -> row.seconds).reversed()
                .thenComparing(row -> row.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    @Nonnull
    private String resolveDisplayName(@Nonnull UUID id) {
        Universe universe = Universe.get();
        PlayerRef online = universe == null ? null : universe.getPlayer(id);
        if (online != null && online.getUsername() != null && !online.getUsername().isBlank()) {
            return online.getUsername();
        }
        PlayerDataModel data = storage.getPlayerData(id);
        String stored = data.getLastKnownName();
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return id.toString();
    }

    @Nullable
    private PlaytimeRewardModel findRewardById(@Nullable String rewardId) {
        if (rewardId == null || rewardId.isBlank()) {
            return null;
        }
        for (PlaytimeRewardModel reward : config.getPlaytimeRewards()) {
            if (reward.getId().equalsIgnoreCase(rewardId.trim())) {
                return reward;
            }
        }
        return null;
    }

    private void loadDraftFromReward(@Nonnull PlaytimeRewardModel reward) {
        draftIdInput = reward.getId();
        draftRequiredInput = formatDurationToken(reward.getRequiredSeconds());
        draftCostInput = String.valueOf(reward.getRequiredCost());
        draftRankInput = reward.getRank();
        draftAutoClaim = reward.isAutoClaim();
        draftCommandsInput = reward.getCommands().isEmpty() ? "" : String.join(" | ", reward.getCommands());
        draftBroadcastInput = reward.getBroadcastMessage();
    }

    private int indexOfReward(@Nonnull List<PlaytimeRewardModel> rewards, @Nonnull String rewardId) {
        for (int i = 0; i < rewards.size(); i++) {
            if (rewards.get(i).getId().equalsIgnoreCase(rewardId.trim())) {
                return i;
            }
        }
        return -1;
    }

    private long parseDurationInput(@Nullable String raw) {
        if (raw == null) {
            return -1L;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return -1L;
        }
        if (normalized.matches("\\d+")) {
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        if (normalized.endsWith("ms")) {
            String value = normalized.substring(0, normalized.length() - 2).trim();
            if (value.matches("\\d+")) {
                try {
                    return Long.parseLong(value) / 1000L;
                } catch (NumberFormatException ignored) {
                    return -1L;
                }
            }
        }
        return TimeUtil.parseDurationSeconds(normalized);
    }

    @Nonnull
    private List<String> parseCommands(@Nullable String raw) {
        List<String> commands = new ArrayList<>();
        if (raw == null) {
            return commands;
        }
        String normalized = raw.replace("\r", "\n");
        for (String segment : normalized.split("\\|")) {
            String cleaned = segment.replace('\n', ' ').trim();
            if (!cleaned.isBlank()) {
                commands.add(cleaned);
            }
        }
        return commands;
    }

    @Nullable
    private Integer parsePositiveInt(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().replace(",", "");
        if (normalized.isBlank() || !normalized.matches("\\d+")) {
            return null;
        }
        try {
            int value = Integer.parseInt(normalized);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private Long parseOptionalNonNegativeLong(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().replace(",", "");
        if (normalized.isBlank() || !normalized.matches("\\d+")) {
            return null;
        }
        try {
            return Math.max(0L, Long.parseLong(normalized));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nonnull
    private String formatDurationToken(long seconds) {
        long clamped = Math.max(0L, seconds);
        if (clamped == 0L) {
            return "0s";
        }
        if (clamped % 3600L == 0L) {
            return (clamped / 3600L) + "h";
        }
        if (clamped % 60L == 0L) {
            return (clamped / 60L) + "m";
        }
        return clamped + "s";
    }

    private void setStatus(@Nonnull String message, @Nonnull String color) {
        statusMessage = message;
        statusColor = color;
    }

    @Nonnull
    private Tab parseTab(@Nonnull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "rewards" -> Tab.REWARDS;
            case "top" -> Tab.TOP;
            case "config" -> Tab.CONFIG;
            default -> Tab.DASHBOARD;
        };
    }

    private enum Tab {
        DASHBOARD,
        REWARDS,
        TOP,
        CONFIG
    }

    private static final class PlaytimeEntry {
        private final UUID uuid;
        private final String name;
        private final long seconds;

        private PlaytimeEntry(@Nonnull UUID uuid, @Nonnull String name, long seconds) {
            this.uuid = uuid;
            this.name = name;
            this.seconds = Math.max(0L, seconds);
        }
    }

    public static final class AdminEventData {
        public static final BuilderCodec<AdminEventData> CODEC = BuilderCodec
                .builder(AdminEventData.class, AdminEventData::new)
                .append(new KeyedCodec<>("Tab", Codec.STRING), (d, v) -> d.tab = v, d -> d.tab).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("RewardAction", Codec.STRING), (d, v) -> d.rewardAction = v, d -> d.rewardAction).add()
                .append(new KeyedCodec<>("RewardId", Codec.STRING), (d, v) -> d.rewardId = v, d -> d.rewardId).add()
                .append(new KeyedCodec<>("@RewardSearch", Codec.STRING), (d, v) -> d.rewardSearch = v, d -> d.rewardSearch).add()
                .append(new KeyedCodec<>("@DraftId", Codec.STRING), (d, v) -> d.draftId = v, d -> d.draftId).add()
                .append(new KeyedCodec<>("@DraftRequired", Codec.STRING), (d, v) -> d.draftRequired = v, d -> d.draftRequired).add()
                .append(new KeyedCodec<>("@DraftCost", Codec.STRING), (d, v) -> d.draftCost = v, d -> d.draftCost).add()
                .append(new KeyedCodec<>("@DraftRank", Codec.STRING), (d, v) -> d.draftRank = v, d -> d.draftRank).add()
                .append(new KeyedCodec<>("@DraftCommands", Codec.STRING), (d, v) -> d.draftCommands = v, d -> d.draftCommands).add()
                .append(new KeyedCodec<>("@DraftBroadcast", Codec.STRING), (d, v) -> d.draftBroadcast = v, d -> d.draftBroadcast).add()
                .append(new KeyedCodec<>("@CfgTopLimit", Codec.STRING), (d, v) -> d.cfgTopLimit = v, d -> d.cfgTopLimit).add()
                .append(new KeyedCodec<>("@CfgCheckInterval", Codec.STRING), (d, v) -> d.cfgCheckInterval = v, d -> d.cfgCheckInterval).add()
                .append(new KeyedCodec<>("@CfgRankupAutoCheck", Codec.STRING), (d, v) -> d.cfgRankupAutoCheck = v, d -> d.cfgRankupAutoCheck).add()
                .build();

        @Nullable
        private String tab;
        @Nullable
        private String action;
        @Nullable
        private String rewardAction;
        @Nullable
        private String rewardId;
        @Nullable
        private String rewardSearch;
        @Nullable
        private String draftId;
        @Nullable
        private String draftRequired;
        @Nullable
        private String draftCost;
        @Nullable
        private String draftRank;
        @Nullable
        private String draftCommands;
        @Nullable
        private String draftBroadcast;
        @Nullable
        private String cfgTopLimit;
        @Nullable
        private String cfgCheckInterval;
        @Nullable
        private String cfgRankupAutoCheck;
    }
}
