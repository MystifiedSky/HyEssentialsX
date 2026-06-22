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

        List<BalanceEntry> entries = loadEntries();
        cmd.set("#EntryCount.Text", entries.size() + " Players");

        buildEntries(cmd, evt, entries);

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
        if (entries.size() > DEFAULT_LIMIT) {
            return entries.subList(0, DEFAULT_LIMIT);
        }
        return entries;
    }

    private void buildEntries(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull List<BalanceEntry> entries) {
        cmd.clear("#EntryList");

        if (entries.isEmpty()) {
            cmd.appendInline("#EntryList",
                    "Label { Text: \"No balances yet.\"; " +
                            "Style: (FontSize: 13, TextColor: #666666, HorizontalAlignment: Center); " +
                            "Anchor: (Top: 30); }");
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            BalanceEntry entry = entries.get(i);
            cmd.append("#EntryList", ROW_LAYOUT);
            String selector = "#EntryList[" + i + "]";
            String line = String.format(Locale.ROOT, "#%d  %s  %s", (i + 1), entry.name, economy.formatAmount(entry.balance));
            cmd.set(selector + ".Text", line);
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

