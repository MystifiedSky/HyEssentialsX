package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.StatsManager;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StatsUI extends InteractiveCustomUIPage<StatsUI.StatsEventData> {

    private static final String LAYOUT = "hyessentialsx/StatsPage.ui";
    private static final String GENERAL_ROW = "hyessentialsx/StatsGeneralRow.ui";
    private static final String ITEM_ROW = "hyessentialsx/StatsItemRow.ui";
    private static final String MOB_ROW = "hyessentialsx/StatsMobRow.ui";

    private final PlayerRef viewer;
    private final UUID targetId;
    private final String targetName;
    private final StatsManager stats;
    private Tab currentTab;

    public StatsUI(@Nonnull PlayerRef viewer,
                   @Nonnull UUID targetId,
                   @Nonnull String targetName,
                   @Nonnull StatsManager stats,
                   @Nonnull Tab initialTab) {
        super(viewer, CustomPageLifetime.CanDismiss, StatsEventData.CODEC);
        this.viewer = viewer;
        this.targetId = targetId;
        this.targetName = targetName;
        this.stats = stats;
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
                                @Nonnull StatsEventData data) {
        if (data.action != null && "Close".equalsIgnoreCase(data.action)) {
            close();
            return;
        }
        if (data.tab != null) {
            currentTab = parseTab(data.tab);
            refresh();
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
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabGeneral",
                EventData.of("Tab", "General"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabItems",
                EventData.of("Tab", "Items"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabMobs",
                EventData.of("Tab", "Mobs"), false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd) {
        cmd.set("#Title.Text", "Player Statistics");
        cmd.set("#Subtitle.Text", targetName);
        cmd.set("#TabGeneral.Text", currentTab == Tab.GENERAL ? "GENERAL *" : "GENERAL");
        cmd.set("#TabItems.Text", currentTab == Tab.ITEMS ? "ITEMS *" : "ITEMS");
        cmd.set("#TabMobs.Text", currentTab == Tab.MOBS ? "MOBS *" : "MOBS");
        cmd.set("#GeneralContent.Visible", currentTab == Tab.GENERAL);
        cmd.set("#ItemsContent.Visible", currentTab == Tab.ITEMS);
        cmd.set("#MobsContent.Visible", currentTab == Tab.MOBS);
        buildOverviewCards(cmd);
        buildGeneral(cmd);
        buildItems(cmd);
        buildMobs(cmd);
    }

    private void buildOverviewCards(@Nonnull UICommandBuilder cmd) {
        cmd.set("#PlayTimeValue.Text", TimeUtil.formatDurationSeconds(custom("play_time")));
        cmd.set("#DeathsValue.Text", stats.formatNumber(custom("deaths")));
        cmd.set("#KillsValue.Text", stats.formatNumber(custom("player_kills") + custom("mob_kills")));
        cmd.set("#DistanceValue.Text", stats.formatDistance(custom("distance_traveled")));
    }

    private void buildGeneral(@Nonnull UICommandBuilder cmd) {
        cmd.clear("#GeneralList");
        List<StatLine> lines = new ArrayList<>();
        lines.add(new StatLine("Play Time", TimeUtil.formatDurationSeconds(custom("play_time")), "#54e39e"));
        lines.add(new StatLine("Times Connected", stats.formatNumber(custom("times_connected")), "#8fd3ff"));
        lines.add(new StatLine("Messages Sent", stats.formatNumber(custom("messages_sent")), "#d0bbff"));
        lines.add(new StatLine("Deaths", stats.formatNumber(custom("deaths")), "#ff8d9a"));
        lines.add(new StatLine("Player Kills", stats.formatNumber(custom("player_kills")), "#f2be5c"));
        lines.add(new StatLine("Mob Kills", stats.formatNumber(custom("mob_kills")), "#f2be5c"));
        lines.add(new StatLine("Damage Dealt", stats.formatNumber(custom("damage_dealt")), "#ffb36b"));
        lines.add(new StatLine("Damage Taken", stats.formatNumber(custom("damage_taken")), "#ff8d9a"));
        lines.add(new StatLine("Distance Traveled", stats.formatDistance(custom("distance_traveled")), "#8fd3ff"));

        Map<String, Long> custom = new LinkedHashMap<>(stats.getCategory(targetId, StatsManager.CATEGORY_CUSTOM));
        for (String known : List.of("play_time", "times_connected", "messages_sent", "deaths", "player_kills",
                "mob_kills", "damage_dealt", "damage_taken", "distance_traveled")) {
            custom.remove(known);
        }
        custom.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> lines.add(new StatLine(stats.displayName(entry.getKey()), stats.formatNumber(entry.getValue()), "#c8d8ef")));

        if (lines.isEmpty()) {
            cmd.set("#GeneralEmpty.Visible", true);
            return;
        }
        cmd.set("#GeneralEmpty.Visible", false);
        for (int i = 0; i < lines.size(); i++) {
            StatLine line = lines.get(i);
            cmd.append("#GeneralList", GENERAL_ROW);
            String row = "#GeneralList[" + i + "]";
            cmd.set(row + " #StatName.Text", line.name());
            cmd.set(row + " #StatValue.Text", line.value());
            cmd.set(row + " #StatValue.Style.TextColor", line.color());
        }
    }

    private void buildItems(@Nonnull UICommandBuilder cmd) {
        cmd.clear("#ItemList");
        List<ItemRow> rows = collectItemRows();
        cmd.set("#ItemCount.Text", rows.size() + " tracked items");
        if (rows.isEmpty()) {
            cmd.set("#ItemsEmpty.Visible", true);
            return;
        }
        cmd.set("#ItemsEmpty.Visible", false);
        for (int i = 0; i < rows.size(); i++) {
            ItemRow item = rows.get(i);
            cmd.append("#ItemList", ITEM_ROW);
            String row = "#ItemList[" + i + "]";
            cmd.set(row + " #ItemName.Text", item.displayName());
            cmd.set(row + " #ItemDetails.Text", "Mined " + stats.formatNumber(item.mined())
                    + "   Placed " + stats.formatNumber(item.placed())
                    + "   Crafted " + stats.formatNumber(item.crafted())
                    + "   Picked Up " + stats.formatNumber(item.pickedUp())
                    + "   Dropped " + stats.formatNumber(item.dropped()));
            if (item.iconItemId() != null) {
                cmd.set(row + " #ItemIcon.ItemId", item.iconItemId());
                cmd.set(row + " #IconFallback.Visible", false);
            } else {
                cmd.set(row + " #IconFallback.Text", item.displayName().isBlank() ? "?" : item.displayName().substring(0, 1).toUpperCase(Locale.ROOT));
                cmd.set(row + " #ItemIcon.Visible", false);
            }
        }
    }

    private void buildMobs(@Nonnull UICommandBuilder cmd) {
        cmd.clear("#MobList");
        List<MobRow> rows = collectMobRows();
        cmd.set("#MobCount.Text", rows.size() + " tracked mobs");
        if (rows.isEmpty()) {
            cmd.set("#MobsEmpty.Visible", true);
            return;
        }
        cmd.set("#MobsEmpty.Visible", false);
        for (int i = 0; i < rows.size(); i++) {
            MobRow mob = rows.get(i);
            cmd.append("#MobList", MOB_ROW);
            String row = "#MobList[" + i + "]";
            cmd.set(row + " #MobName.Text", mob.displayName());
            cmd.set(row + " #MobDetails.Text", "Killed " + stats.formatNumber(mob.killed())
                    + "   Died To " + stats.formatNumber(mob.killedBy()));
            cmd.set(row + " #MobInitial.Text", mob.displayName().isBlank() ? "?" : mob.displayName().substring(0, 1).toUpperCase(Locale.ROOT));
        }
    }

    @Nonnull
    private List<ItemRow> collectItemRows() {
        Map<String, ItemMutable> rows = new LinkedHashMap<>();
        mergeItems(rows, StatsManager.CATEGORY_MINED, ItemMutable::addMined);
        mergeItems(rows, StatsManager.CATEGORY_PLACED, ItemMutable::addPlaced);
        mergeItems(rows, "crafted", ItemMutable::addCrafted);
        mergeItems(rows, "picked_up", ItemMutable::addPickedUp);
        mergeItems(rows, "dropped", ItemMutable::addDropped);

        return rows.entrySet().stream()
                .map(entry -> entry.getValue().toRow(entry.getKey(), resolveItemDisplay(entry.getKey())))
                .sorted(Comparator.comparingLong(ItemRow::total).reversed()
                        .thenComparing(ItemRow::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void mergeItems(@Nonnull Map<String, ItemMutable> rows,
                            @Nonnull String category,
                            @Nonnull ItemUpdater updater) {
        stats.getCategory(targetId, category).forEach((id, value) -> {
            if (id == null || id.isBlank() || value == null || value <= 0L) return;
            ItemMutable row = rows.computeIfAbsent(id, ignored -> new ItemMutable());
            updater.apply(row, value);
        });
    }

    @Nonnull
    private List<MobRow> collectMobRows() {
        Map<String, Long> killed = stats.getCategory(targetId, StatsManager.CATEGORY_KILLED);
        Map<String, Long> killedBy = stats.getCategory(targetId, StatsManager.CATEGORY_KILLED_BY);
        java.util.Set<String> ids = new java.util.HashSet<>();
        ids.addAll(killed.keySet());
        ids.addAll(killedBy.keySet());
        return ids.stream()
                .map(id -> new MobRow(stats.displayName(id), killed.getOrDefault(id, 0L), killedBy.getOrDefault(id, 0L)))
                .sorted(Comparator.comparingLong(MobRow::total).reversed()
                        .thenComparing(MobRow::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Nonnull
    private ItemDisplay resolveItemDisplay(@Nonnull String id) {
        Item item = Item.getAssetMap().getAsset(id);
        if (item != null) {
            return new ItemDisplay(resolveItemName(item, id), item.getId());
        }
        BlockType block = BlockType.getAssetMap().getAsset(id);
        if (block != null && block.getItem() != null) {
            Item blockItem = block.getItem();
            return new ItemDisplay(resolveItemName(blockItem, id), blockItem.getId());
        }
        return new ItemDisplay(stats.displayName(id), null);
    }

    @Nonnull
    private String resolveItemName(@Nonnull Item item, @Nonnull String fallback) {
        String key = item.getTranslationKey();
        if (key != null && !key.isBlank()) {
            String id = item.getId();
            return id == null || id.isBlank() ? stats.displayName(fallback) : stats.displayName(id);
        }
        String id = item.getId();
        return id == null || id.isBlank() ? stats.displayName(fallback) : stats.displayName(id);
    }

    private long custom(@Nonnull String stat) {
        return stats.get(targetId, StatsManager.CATEGORY_CUSTOM, stat);
    }

    @Nonnull
    private Tab parseTab(@Nonnull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "items" -> Tab.ITEMS;
            case "mobs" -> Tab.MOBS;
            default -> Tab.GENERAL;
        };
    }

    public enum Tab {
        GENERAL,
        ITEMS,
        MOBS
    }

    public static final class StatsEventData {
        public static final BuilderCodec<StatsEventData> CODEC = BuilderCodec
                .builder(StatsEventData.class, StatsEventData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("Tab", Codec.STRING), (d, v) -> d.tab = v, d -> d.tab)
                .build();

        @Nullable
        private String action;
        @Nullable
        private String tab;
    }

    private record StatLine(String name, String value, String color) {}
    private record ItemDisplay(String displayName, @Nullable String iconItemId) {}
    private record ItemRow(String displayName, @Nullable String iconItemId, long mined, long placed, long crafted, long pickedUp, long dropped) {
        private long total() {
            return mined + placed + crafted + pickedUp + dropped;
        }
    }
    private record MobRow(String displayName, long killed, long killedBy) {
        private long total() {
            return killed + killedBy;
        }
    }

    private interface ItemUpdater {
        void apply(ItemMutable row, long value);
    }

    private static final class ItemMutable {
        private long mined;
        private long placed;
        private long crafted;
        private long pickedUp;
        private long dropped;

        private void addMined(long value) { mined += value; }
        private void addPlaced(long value) { placed += value; }
        private void addCrafted(long value) { crafted += value; }
        private void addPickedUp(long value) { pickedUp += value; }
        private void addDropped(long value) { dropped += value; }

        private ItemRow toRow(@Nonnull String id, @Nonnull ItemDisplay display) {
            return new ItemRow(display.displayName() == null || display.displayName().isBlank() ? id : display.displayName(),
                    display.iconItemId(), mined, placed, crafted, pickedUp, dropped);
        }
    }
}
