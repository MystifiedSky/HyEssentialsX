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
import xyz.thelegacyvoyage.hyessentialsx.managers.LeaderboardDefinition;
import xyz.thelegacyvoyage.hyessentialsx.managers.StatsManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public final class LeaderboardUI extends InteractiveCustomUIPage<LeaderboardUI.UIEventData> {

    public static final String EXEMPT_PERMISSION = "hyessentialsx.leaderboard.exempt";

    private static final String LAYOUT = "hyessentialsx/LeaderboardPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/LeaderboardRow.ui";
    private static final int LIMIT = 25;

    private final StatsManager stats;
    private LeaderboardDefinition current;

    public LeaderboardUI(@Nonnull PlayerRef playerRef,
                         @Nonnull StatsManager stats,
                         @Nonnull LeaderboardDefinition initial) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.stats = stats;
        this.current = initial;
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
                                @Nonnull UIEventData data) {
        if (data.action != null && data.action.equals("Close")) {
            close();
            return;
        }
        if (data.stat != null && !data.stat.isBlank()) {
            LeaderboardDefinition selected = LeaderboardDefinition.resolve(data.stat);
            if (selected != null) {
                current = selected;
                refresh();
            }
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void bindEvents(@Nonnull UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", "Close"), false);

        List<LeaderboardDefinition> defaults = LeaderboardDefinition.DEFAULTS;
        for (int i = 0; i < defaults.size() && i < STAT_BUTTON_IDS.size(); i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, STAT_BUTTON_IDS.get(i),
                    EventData.of("Stat", defaults.get(i).stat()), false);
        }
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        rebuild(cmd);
        sendUpdate(cmd, false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd) {
        List<StatsManager.LeaderboardEntry> entries = stats.topPlayers(
                current.category(),
                current.stat(),
                LIMIT,
                EXEMPT_PERMISSION
        );
        long total = sum(entries);

        cmd.set("#Subtitle.Text", "Viewing " + current.display());
        cmd.set("#PlayerCount.Text", String.valueOf(entries.size()));
        cmd.set("#TopTotal.Text", current.distance() ? stats.formatDistance(total) : stats.formatNumber(total));
        cmd.set("#ValueHeader.Text", current.display());
        cmd.set("#ShowingInfo.Text", "Showing " + entries.size() + " players");
        buildStatButtons(cmd);
        buildEntries(cmd, entries);
    }

    private void buildStatButtons(@Nonnull UICommandBuilder cmd) {
        List<LeaderboardDefinition> defaults = LeaderboardDefinition.DEFAULTS;
        for (int i = 0; i < defaults.size() && i < STAT_BUTTON_IDS.size(); i++) {
            cmd.set(STAT_BUTTON_IDS.get(i) + ".Text", buttonText(defaults.get(i)));
        }
    }

    @Nonnull
    private String buttonText(@Nonnull LeaderboardDefinition definition) {
        String prefix = definition.key().equals(current.key()) ? "* " : "";
        return prefix + definition.display();
    }

    private void buildEntries(@Nonnull UICommandBuilder cmd,
                              @Nonnull List<StatsManager.LeaderboardEntry> entries) {
        cmd.clear("#EntryList");
        if (entries.isEmpty()) {
            cmd.appendInline("#EntryList",
                    "Group { LayoutMode: Center; Anchor: (Height: 96); " +
                            "Label { Text: \"No tracked players found.\"; " +
                            "Style: (FontSize: 13, TextColor: #8ca7cc, HorizontalAlignment: Center); } }");
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            StatsManager.LeaderboardEntry entry = entries.get(i);
            cmd.append("#EntryList", ROW_LAYOUT);
            String selector = "#EntryList[" + i + "]";
            cmd.set(selector + " #Rank.Text", "#" + (i + 1));
            cmd.set(selector + " #PlayerName.Text", entry.playerName());
            cmd.set(selector + " #Value.Text", current.distance()
                    ? stats.formatDistance(entry.value())
                    : stats.formatNumber(entry.value()));

            String rankColor = switch (i) {
                case 0 -> "#f2be5c";
                case 1 -> "#8fd3ff";
                case 2 -> "#cfa9ff";
                default -> "#8ed8ff";
            };
            cmd.set(selector + " #Rank.Style.TextColor", rankColor);
        }
    }

    private long sum(@Nonnull List<StatsManager.LeaderboardEntry> entries) {
        long total = 0L;
        for (StatsManager.LeaderboardEntry entry : entries) {
            if (Long.MAX_VALUE - total < entry.value()) {
                return Long.MAX_VALUE;
            }
            total += entry.value();
        }
        return total;
    }

    private static final List<String> STAT_BUTTON_IDS = List.of(
            "#StatPlayerKills",
            "#StatMobKills",
            "#StatDeaths",
            "#StatDamageDealt",
            "#StatDamageTaken",
            "#StatDistance",
            "#StatMessages",
            "#StatConnections",
            "#StatDrops"
    );

    public static final class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec
                .builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Stat", Codec.STRING), (d, v) -> d.stat = v, d -> d.stat).add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String stat;
    }
}
