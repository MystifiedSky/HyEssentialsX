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
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlaytimeRewardModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PlaytimeUI extends InteractiveCustomUIPage<PlaytimeUI.PlaytimeEventData> {

    private static final String LAYOUT = "hyessentialsx/PlaytimePage.ui";
    private static final String REWARD_ROW_LAYOUT = "hyessentialsx/PlaytimeRewardRow.ui";
    private static final String TOP_ROW_LAYOUT = "hyessentialsx/PlaytimeTopRow.ui";

    private final PlayerRef playerRef;
    private final PlaytimeManager playtime;
    private final StorageManager storage;
    private final ConfigManager config;
    private Tab currentTab;

    public PlaytimeUI(@Nonnull PlayerRef playerRef,
                      @Nonnull PlaytimeManager playtime,
                      @Nonnull StorageManager storage,
                      @Nonnull ConfigManager config,
                      @Nonnull Tab initialTab) {
        super(playerRef, CustomPageLifetime.CanDismiss, PlaytimeEventData.CODEC);
        this.playerRef = playerRef;
        this.playtime = playtime;
        this.storage = storage;
        this.config = config;
        this.currentTab = initialTab;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        bindEvents(events);
        rebuild(cmd);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PlaytimeEventData data) {
        if (data.tab != null) {
            currentTab = parseTab(data.tab);
            refresh();
            return;
        }
        if (data.action == null) {
            return;
        }
        if ("Close".equalsIgnoreCase(data.action)) {
            close();
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        rebuild(cmd);
        sendUpdate(cmd, false);
    }

    private void bindEvents(@Nonnull UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", "Close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabOverview",
                EventData.of("Tab", "Overview"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabRewards",
                EventData.of("Tab", "Rewards"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabTop",
                EventData.of("Tab", "Top"), false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd) {
        cmd.set("#Title.Text", "Playtime Center");
        cmd.set("#Subtitle.Text", "Track your progress, rewards, and leaderboard rank");

        cmd.set("#OverviewContent.Visible", currentTab == Tab.OVERVIEW);
        cmd.set("#RewardsContent.Visible", currentTab == Tab.REWARDS);
        cmd.set("#TopContent.Visible", currentTab == Tab.TOP);

        cmd.set("#TabOverview.Text", currentTab == Tab.OVERVIEW ? "OVERVIEW *" : "OVERVIEW");
        cmd.set("#TabRewards.Text", currentTab == Tab.REWARDS ? "REWARDS *" : "REWARDS");
        cmd.set("#TabTop.Text", currentTab == Tab.TOP ? "TOP *" : "TOP");

        buildOverview(cmd);
        buildRewards(cmd);
        buildTop(cmd);
    }

    private void buildOverview(@Nonnull UICommandBuilder cmd) {
        List<PlaytimeEntry> entries = collectEntries();
        long myPlaytime = playtime.getPlaytimeSeconds(playerRef.getUuid());
        int myRank = rankFor(entries, playerRef.getUuid());
        int readyRewards = countReadyRewards(myPlaytime);

        cmd.set("#MyPlaytimeValue.Text", TimeUtil.formatDurationSeconds(myPlaytime));
        cmd.set("#MyRankValue.Text", myRank > 0 ? "#" + myRank : "N/A");
        cmd.set("#PlayerCountValue.Text", String.valueOf(entries.size()));
        cmd.set("#RewardsReadyValue.Text", String.valueOf(readyRewards));
    }

    private void buildRewards(@Nonnull UICommandBuilder cmd) {
        cmd.clear("#RewardList");
        List<PlaytimeRewardModel> rewards = sortedRewards();
        if (rewards.isEmpty()) {
            cmd.appendInline("#RewardList",
                    "Label { Text: \"No playtime rewards configured.\"; " +
                            "Style: (FontSize: 12, TextColor: #8da8cc); }");
            cmd.set("#RewardSummary.Text", "Configure rewards in /playtime admin");
            return;
        }

        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        long myPlaytime = playtime.getPlaytimeSeconds(playerRef.getUuid());
        int available = 0;

        for (int i = 0; i < rewards.size(); i++) {
            PlaytimeRewardModel reward = rewards.get(i);
            boolean claimed = data.hasClaimedPlaytimeReward(reward.getId());
            boolean ready = !claimed && myPlaytime >= reward.getRequiredSeconds();
            if (ready && reward.isAutoClaim()) {
                available++;
            }

            cmd.append("#RewardList", REWARD_ROW_LAYOUT);
            String row = "#RewardList[" + i + "]";
            cmd.set(row + " #RewardId.Text", reward.getId());
            cmd.set(row + " #RewardRequirement.Text", TimeUtil.formatDurationSeconds(reward.getRequiredSeconds()));
            String status = claimed ? "Claimed" : (ready ? (reward.isAutoClaim() ? "Ready" : "Manual") : "Locked");
            cmd.set(row + " #RewardStatus.Text", status);
            String statusColor = switch (status) {
                case "Claimed" -> "#7f9fc6";
                case "Ready" -> "#58e39f";
                case "Manual" -> "#8fd3ff";
                default -> "#f2be5c";
            };
            cmd.set(row + " #RewardStatus.Style.TextColor", statusColor);
        }

        cmd.set("#RewardSummary.Text", "Available now: " + available + " / " + rewards.size());
    }

    private void buildTop(@Nonnull UICommandBuilder cmd) {
        List<PlaytimeEntry> entries = collectEntries();
        cmd.clear("#TopList");

        if (entries.isEmpty()) {
            cmd.appendInline("#TopList",
                    "Label { Text: \"No playtime data found.\"; Style: (FontSize: 12, TextColor: #8da8cc); }");
            cmd.set("#SelfRank.Text", "Your Rank: N/A");
            cmd.set("#SelfTime.Text", "Your Time: 0s");
            return;
        }

        int limit = Math.min(entries.size(), Math.max(10, config.getPlaytimeTopLimit()));
        for (int i = 0; i < limit; i++) {
            PlaytimeEntry entry = entries.get(i);
            cmd.append("#TopList", TOP_ROW_LAYOUT);
            String row = "#TopList[" + i + "]";
            cmd.set(row + " #Rank.Text", "#" + (i + 1));
            cmd.set(row + " #PlayerName.Text", entry.name);
            cmd.set(row + " #Playtime.Text", TimeUtil.formatDurationSeconds(entry.playtimeSeconds));

            String rankColor = switch (i) {
                case 0 -> "#f2be5c";
                case 1 -> "#8fd3ff";
                case 2 -> "#d2b4ff";
                default -> "#8ed8ff";
            };
            cmd.set(row + " #Rank.Style.TextColor", rankColor);
        }

        int myRank = rankFor(entries, playerRef.getUuid());
        long myPlaytime = playtime.getPlaytimeSeconds(playerRef.getUuid());
        cmd.set("#SelfRank.Text", myRank > 0 ? "Your Rank: #" + myRank : "Your Rank: N/A");
        cmd.set("#SelfTime.Text", "Your Time: " + TimeUtil.formatDurationSeconds(myPlaytime));
    }

    @Nonnull
    private List<PlaytimeEntry> collectEntries() {
        Set<UUID> ids = new java.util.HashSet<>(storage.listPlayerIds());
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
            long seconds = Math.max(0L, playtime.getPlaytimeSeconds(id));
            String name = resolveDisplayName(id);
            out.add(new PlaytimeEntry(id, name, seconds));
        }
        out.sort(Comparator.comparingLong((PlaytimeEntry entry) -> entry.playtimeSeconds).reversed()
                .thenComparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    @Nonnull
    private List<PlaytimeRewardModel> sortedRewards() {
        List<PlaytimeRewardModel> rewards = new ArrayList<>(config.getPlaytimeRewards());
        rewards.sort(Comparator.comparingLong(PlaytimeRewardModel::getRequiredSeconds)
                .thenComparing(r -> r.getId().toLowerCase(Locale.ROOT)));
        return rewards;
    }

    private int rankFor(@Nonnull List<PlaytimeEntry> entries, @Nonnull UUID uuid) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).uuid.equals(uuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    private int countReadyRewards(long playtimeSeconds) {
        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        int count = 0;
        for (PlaytimeRewardModel reward : config.getPlaytimeRewards()) {
            if (reward.getId().isBlank()) continue;
            if (!reward.isAutoClaim()) continue;
            if (data.hasClaimedPlaytimeReward(reward.getId())) continue;
            if (playtimeSeconds >= reward.getRequiredSeconds()) {
                count++;
            }
        }
        return count;
    }

    @Nonnull
    private String resolveDisplayName(@Nonnull UUID uuid) {
        Universe universe = Universe.get();
        PlayerRef online = universe == null ? null : universe.getPlayer(uuid);
        if (online != null && online.getUsername() != null && !online.getUsername().isBlank()) {
            return online.getUsername();
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        String name = data.getLastKnownName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return uuid.toString();
    }

    @Nonnull
    private Tab parseTab(@Nonnull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "rewards" -> Tab.REWARDS;
            case "top" -> Tab.TOP;
            default -> Tab.OVERVIEW;
        };
    }

    public enum Tab {
        OVERVIEW,
        REWARDS,
        TOP
    }

    private static final class PlaytimeEntry {
        private final UUID uuid;
        private final String name;
        private final long playtimeSeconds;

        private PlaytimeEntry(@Nonnull UUID uuid, @Nonnull String name, long playtimeSeconds) {
            this.uuid = uuid;
            this.name = name;
            this.playtimeSeconds = Math.max(0L, playtimeSeconds);
        }
    }

    public static final class PlaytimeEventData {
        public static final BuilderCodec<PlaytimeEventData> CODEC = BuilderCodec
                .builder(PlaytimeEventData.class, PlaytimeEventData::new)
                .addField(new KeyedCodec<>("Tab", Codec.STRING), (d, v) -> d.tab = v, d -> d.tab)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .build();

        @Nullable
        private String tab;
        @Nullable
        private String action;
    }
}
