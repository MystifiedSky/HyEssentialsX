package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.google.gson.Gson;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BalTopUI extends CustomUIPage {

    private static final String LAYOUT = "hyessentialsx/BalTopPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/BalTopRow.ui";
    private static final int DEFAULT_LIMIT = 10;

    private final PlayerRef playerRef;
    private final EconomyManager economy;
    private final StorageManager storage;
    private final Gson gson = new Gson();

    public BalTopUI(@Nonnull PlayerRef playerRef,
                    @Nonnull EconomyManager economy,
                    @Nonnull StorageManager storage) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.playerRef = playerRef;
        this.economy = economy;
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

        List<BalanceEntry> allEntries = loadEntries();
        List<BalanceEntry> displayEntries = limitEntries(allEntries);
        long totalTopBalance = sumBalances(displayEntries);

        cmd.set("#EntryCount.Text", String.valueOf(allEntries.size()));
        cmd.set("#TotalBalance.Text", economy.formatAmount(totalTopBalance));
        cmd.set("#Subtitle.Text", "Top " + displayEntries.size() + " " + resolveCurrencyName() + " Holders");
        cmd.set("#ShowingInfo.Text", "Showing " + displayEntries.size() + " of " + allEntries.size() + " players");

        buildEntries(cmd, displayEntries);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("action", "close"),
                false
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            String data
    ) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Map<?, ?> payload;
        try {
            payload = gson.fromJson(data, Map.class);
        } catch (Exception e) {
            return;
        }
        if (payload == null) {
            return;
        }
        Object actionObj = payload.get("action");
        if (!(actionObj instanceof String)) {
            return;
        }
        String action = (String) actionObj;
        if (action.isEmpty()) {
            return;
        }

        if (action.equals("close")) {
            close();
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private List<BalanceEntry> loadEntries() {
        List<BalanceEntry> entries = new ArrayList<>();
        for (UUID uuid : storage.listPlayerIds()) {
            PlayerRef online = Universe.get().getPlayer(uuid);
            String name = online != null ? online.getUsername() : null;
            PlayerDataModel data = storage.getPlayerData(uuid);
            if (name == null || name.isBlank()) {
                name = data.getLastKnownName();
            }
            if (name == null || name.isBlank()) {
                name = uuid.toString();
            }
            long balance = Math.max(0L, data.getBalance());
            entries.add(new BalanceEntry(name, balance));
        }
        entries.sort(Comparator.comparingLong((BalanceEntry entry) -> entry.balance).reversed()
                .thenComparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
        return entries;
    }

    @Nonnull
    private List<BalanceEntry> limitEntries(@Nonnull List<BalanceEntry> entries) {
        if (entries.size() <= DEFAULT_LIMIT) {
            return entries;
        }
        return entries.subList(0, DEFAULT_LIMIT);
    }

    private long sumBalances(@Nonnull List<BalanceEntry> entries) {
        long total = 0L;
        for (BalanceEntry entry : entries) {
            if (Long.MAX_VALUE - total < entry.balance) {
                return Long.MAX_VALUE;
            }
            total += entry.balance;
        }
        return total;
    }

    @Nonnull
    private String resolveCurrencyName() {
        String name = economy.getCurrencyName();
        if (name == null) {
            return "Currency";
        }
        String trimmed = name.trim();
        if (trimmed.isBlank()) {
            return "Currency";
        }
        return trimmed;
    }

    private void buildEntries(@Nonnull UICommandBuilder cmd, @Nonnull List<BalanceEntry> entries) {
        cmd.clear("#EntryList");

        if (entries.isEmpty()) {
            cmd.appendInline("#EntryList",
                    "Group { LayoutMode: Center; Anchor: (Height: 96); " +
                            "Label { Text: \"No balances yet.\"; " +
                            "Style: (FontSize: 13, TextColor: #8ca7cc, HorizontalAlignment: Center); } }");
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            BalanceEntry entry = entries.get(i);
            cmd.append("#EntryList", ROW_LAYOUT);
            String selector = "#EntryList[" + i + "]";
            cmd.set(selector + " #Rank.Text", "#" + (i + 1));
            cmd.set(selector + " #PlayerName.Text", entry.name);
            cmd.set(selector + " #Balance.Text", economy.formatAmount(entry.balance));

            String rankColor = switch (i) {
                case 0 -> "#f2be5c";
                case 1 -> "#8fd3ff";
                case 2 -> "#cfa9ff";
                default -> "#8ed8ff";
            };
            cmd.set(selector + " #Rank.Style.TextColor", rankColor);
        }
    }

    private static final class BalanceEntry {
        private final String name;
        private final long balance;

        private BalanceEntry(@Nonnull String name, long balance) {
            this.name = name;
            this.balance = balance;
        }
    }
}

