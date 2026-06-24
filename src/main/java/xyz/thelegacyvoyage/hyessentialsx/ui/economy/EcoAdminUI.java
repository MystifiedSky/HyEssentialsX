package xyz.thelegacyvoyage.hyessentialsx.ui.economy;

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
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyAuditManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyHudManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.EconomyAuditEntryModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.UiBackTarget;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EcoAdminUI extends InteractiveCustomUIPage<EcoAdminUI.AdminEventData> {

    private static final String LAYOUT = "hyessentialsx/EcoAdminPage.ui";
    private static final String PLAYER_ROW_LAYOUT = "hyessentialsx/EcoAdminPlayerEntry.ui";
    private static final String TOP_ROW_LAYOUT = "hyessentialsx/EcoAdminTopEntry.ui";
    private static final int PLAYER_PAGE_SIZE = 12;
    private static final int LOG_PAGE_SIZE = 30;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final PlayerRef playerRef;
    private final EconomyManager economy;
    private final StorageManager storage;
    private final ConfigManager config;
    private final EconomyHudManager hudManager;
    private final EconomyAuditManager audit;
    @Nullable
    private final UiBackTarget backTarget;

    private Tab currentTab = Tab.DASHBOARD;
    private String searchQuery = "";
    private String logFilter = "";
    private String amountInput = "";
    private int playerPage = 0;
    private int logPage = 0;
    @Nullable
    private String selectedPlayerUuid;
    @Nullable
    private String selectedPlayerName;

    private String cfgCurrencySymbol = "$";
    private String cfgStartingBalance = "0";
    private String cfgHudLabel = "HyCoins";
    private String cfgHudInterval = "1000";
    private boolean cfgHudEnabled = true;
    private boolean cfgHudDefaultHidden = false;
    private String cfgHudAnchor = "bottom_right";

    private String statusMessage = "";
    private String statusColor = "#88aaff";

    public EcoAdminUI(@Nonnull PlayerRef playerRef,
                      @Nonnull EconomyManager economy,
                      @Nonnull StorageManager storage,
                      @Nonnull ConfigManager config,
                      @Nonnull EconomyHudManager hudManager,
                      @Nonnull EconomyAuditManager audit) {
        this(playerRef, economy, storage, config, hudManager, audit, null);
    }

    public EcoAdminUI(@Nonnull PlayerRef playerRef,
                      @Nonnull EconomyManager economy,
                      @Nonnull StorageManager storage,
                      @Nonnull ConfigManager config,
                      @Nonnull EconomyHudManager hudManager,
                      @Nonnull EconomyAuditManager audit,
                      @Nullable UiBackTarget backTarget) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminEventData.CODEC);
        this.playerRef = playerRef;
        this.economy = economy;
        this.storage = storage;
        this.config = config;
        this.hudManager = hudManager;
        this.audit = audit;
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
        if (data.searchQuery != null) {
            String normalized = data.searchQuery.trim().toLowerCase(Locale.ROOT);
            if (!searchQuery.equals(normalized)) {
                playerPage = 0;
            }
            searchQuery = normalized;
            refresh();
            return;
        }
        if (data.logFilter != null) {
            String normalized = data.logFilter.trim().toLowerCase(Locale.ROOT);
            if (!logFilter.equals(normalized)) {
                logPage = 0;
            }
            logFilter = normalized;
            refresh();
            return;
        }
        if (data.amountInput != null) {
            amountInput = data.amountInput.trim();
            refresh();
            return;
        }
        if ("Select".equalsIgnoreCase(data.playerAction) && data.playerUuid != null) {
            selectedPlayerUuid = data.playerUuid;
            selectedPlayerName = data.playerName;
            refresh();
            return;
        }

        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "Close" -> close();
            case "Back" -> openBack(ref, store);
            case "ForceSave" -> {
                forceSaveData();
                setStatus("Economy data queued for save.", "#55ff55");
                refresh();
            }
            case "PrevPage" -> {
                if (playerPage > 0) {
                    playerPage--;
                }
                refresh();
            }
            case "NextPage" -> {
                playerPage++;
                refresh();
            }
            case "LogPrev" -> {
                if (logPage > 0) {
                    logPage--;
                }
                refresh();
            }
            case "LogNext" -> {
                logPage++;
                refresh();
            }
            case "Give", "Take", "Set", "Reset" -> {
                handlePlayerAction(data.action);
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
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ForceSaveButton",
                EventData.of("Action", "ForceSave"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabDashboard",
                EventData.of("Tab", "Dashboard"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabPlayers",
                EventData.of("Tab", "Players"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabTop",
                EventData.of("Tab", "Top"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabLog",
                EventData.of("Tab", "Log"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlayerSearch",
                EventData.of("@SearchQuery", "#PlayerSearch.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LogFilter",
                EventData.of("@LogFilter", "#LogFilter.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ActionAmount",
                EventData.of("@AmountInput", "#ActionAmount.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
                EventData.of("Action", "PrevPage"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
                EventData.of("Action", "NextPage"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#GiveButton",
                EventData.of("Action", "Give"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TakeButton",
                EventData.of("Action", "Take"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SetButton",
                EventData.of("Action", "Set"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
                EventData.of("Action", "Reset"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#LogPrevButton",
                EventData.of("Action", "LogPrev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LogNextButton",
                EventData.of("Action", "LogNext"), false);

    }

    private void rebuild(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.set("#PlayerSearch.Value", searchQuery);
        cmd.set("#BackToParentButton.Visible", backTarget != null);
        cmd.set("#LogFilter.Value", logFilter);
        cmd.set("#ActionAmount.Value", amountInput);

        cmd.set("#DashboardContent.Visible", currentTab == Tab.DASHBOARD);
        cmd.set("#PlayersContent.Visible", currentTab == Tab.PLAYERS);
        cmd.set("#TopContent.Visible", currentTab == Tab.TOP);
        cmd.set("#LogContent.Visible", currentTab == Tab.LOG);

        buildDashboardTab(cmd);
        buildPlayersTab(cmd, events);
        buildTopTab(cmd);
        buildLogTab(cmd);
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
        long total = 0L;
        int playersWithBalance = 0;
        for (UUID uuid : collectPlayerIds()) {
            long balance = economy.getBalance(uuid);
            if (balance > 0L) {
                playersWithBalance++;
            }
            if (Long.MAX_VALUE - total < balance) {
                total = Long.MAX_VALUE;
            } else {
                total += balance;
            }
        }
        long average = playersWithBalance <= 0 ? 0L : (total / playersWithBalance);
        cmd.set("#TotalCirculating.Text", economy.formatAmount(total));
        cmd.set("#TotalPlayers.Text", String.valueOf(playersWithBalance));
        cmd.set("#AverageBalance.Text", economy.formatAmount(average));
        cmd.set("#ConfigSummary.Text", "Symbol: " + config.getEconomyCurrencySymbol()
                + "   Start: " + economy.formatAmount(config.getEconomyStartingBalance())
                + "   HUD Tick: " + config.getEconomyHudUpdateIntervalMs() + "ms");

        cmd.clear("#ActivityLog");
        List<EconomyAuditEntryModel> recent = audit.recent(8);
        if (recent.isEmpty()) {
            cmd.appendInline("#ActivityLog",
                    "Label { Text: \"No recent activity\"; Style: (FontSize: 12, TextColor: #888888); }");
            return;
        }
        for (EconomyAuditEntryModel entry : recent) {
            String line = "[" + formatTime(entry.getTimestamp()) + "] "
                    + sanitizeName(entry.getActor())
                    + " " + sanitizeName(entry.getAction())
                    + " " + sanitizeName(entry.getTarget())
                    + " " + economy.formatAmount(entry.getAmount());
            cmd.appendInline("#ActivityLog", "Label { Text: \"" + escapeInline(line)
                    + "\"; Style: (FontSize: 11, TextColor: #cccccc); Anchor: (Bottom: 3); }");
        }
    }

    private void buildPlayersTab(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        List<PlayerRow> rows = collectPlayerRows(searchQuery);
        int totalPages = Math.max(1, (int) Math.ceil(rows.size() / (double) PLAYER_PAGE_SIZE));
        if (playerPage >= totalPages) {
            playerPage = totalPages - 1;
        }
        int start = playerPage * PLAYER_PAGE_SIZE;
        int end = Math.min(rows.size(), start + PLAYER_PAGE_SIZE);

        cmd.clear("#PlayerList");
        for (int i = start; i < end; i++) {
            PlayerRow rowData = rows.get(i);
            int displayIndex = i - start;
            cmd.append("#PlayerList", PLAYER_ROW_LAYOUT);
            String row = "#PlayerList[" + displayIndex + "]";
            cmd.set(row + " #PlayerName.Text", rowData.name);
            cmd.set(row + " #PlayerBalance.Text", economy.formatAmount(rowData.balance));
            cmd.set(row + " #SelectionIndicator.Visible",
                    rowData.uuid.toString().equals(selectedPlayerUuid));
            events.addEventBinding(CustomUIEventBindingType.Activating, row,
                    EventData.of("PlayerAction", "Select")
                            .append("PlayerUuid", rowData.uuid.toString())
                            .append("PlayerName", rowData.name), false);
        }

        if (start >= end) {
            cmd.appendInline("#PlayerList",
                    "Label { Text: \"No players found\"; Style: (FontSize: 12, TextColor: #888888); }");
        }

        cmd.set("#PageInfo.Text", "Page " + (playerPage + 1) + "/" + totalPages);
        if (selectedPlayerName == null || selectedPlayerName.isBlank()) {
            cmd.set("#SelectedLabel.Text", "Select a player");
            cmd.set("#SelectedName.Text", "");
        } else {
            cmd.set("#SelectedLabel.Text", "Selected");
            cmd.set("#SelectedName.Text", selectedPlayerName);
        }
    }

    private void buildTopTab(@Nonnull UICommandBuilder cmd) {
        List<PlayerRow> rows = collectPlayerRows("");
        rows.sort(Comparator.comparingLong((PlayerRow row) -> row.balance).reversed()
                .thenComparing(row -> row.name.toLowerCase(Locale.ROOT)));

        cmd.clear("#TopList");
        int limit = Math.min(10, rows.size());
        for (int i = 0; i < limit; i++) {
            PlayerRow row = rows.get(i);
            cmd.append("#TopList", TOP_ROW_LAYOUT);
            String base = "#TopList[" + i + "]";
            cmd.set(base + " #Rank.Text", "#" + (i + 1));
            cmd.set(base + " #PlayerName.Text", row.name);
            cmd.set(base + " #PlayerBalance.Text", economy.formatAmount(row.balance));
        }
        if (limit == 0) {
            cmd.appendInline("#TopList",
                    "Label { Text: \"No balances found\"; Style: (FontSize: 12, TextColor: #888888); }");
        }
    }

    private void buildLogTab(@Nonnull UICommandBuilder cmd) {
        String filter = logFilter.isBlank() ? null : logFilter;
        int totalEntries = audit.count(filter);
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) LOG_PAGE_SIZE));
        if (logPage >= totalPages) {
            logPage = totalPages - 1;
        }
        int offset = logPage * LOG_PAGE_SIZE;
        List<EconomyAuditEntryModel> entries = audit.query(filter, offset, LOG_PAGE_SIZE);

        cmd.set("#LogCountInfo.Text", "Entries: " + totalEntries);
        cmd.set("#LogPageInfo.Text", "Page " + (logPage + 1) + "/" + totalPages);
        cmd.clear("#LogList");
        if (entries.isEmpty()) {
            cmd.appendInline("#LogList",
                    "Label { Text: \"No matching entries\"; Style: (FontSize: 12, TextColor: #888888); }");
            return;
        }
        for (EconomyAuditEntryModel entry : entries) {
            String line = "[" + formatTime(entry.getTimestamp()) + "] "
                    + sanitizeName(entry.getActor())
                    + " -> " + sanitizeName(entry.getTarget())
                    + " " + sanitizeName(entry.getAction())
                    + " " + economy.formatAmount(entry.getAmount())
                    + " (after: " + economy.formatAmount(entry.getBalanceAfter()) + ")";
            if (entry.getDetail() != null && !entry.getDetail().isBlank()) {
                line = line + " [" + entry.getDetail() + "]";
            }
            cmd.appendInline("#LogList", "Label { Text: \"" + escapeInline(line)
                    + "\"; Style: (FontSize: 11, TextColor: #cccccc); Anchor: (Bottom: 3); }");
        }
    }

    private void handlePlayerAction(@Nonnull String action) {
        UUID targetId = parseUuid(selectedPlayerUuid);
        if (targetId == null) {
            setStatus("Select a player first.", "#ff6666");
            return;
        }
        String targetName = selectedPlayerName == null || selectedPlayerName.isBlank()
                ? resolveDisplayName(targetId, targetId.toString())
                : selectedPlayerName;
        String actor = playerRef.getUsername();
        switch (action) {
            case "Give" -> {
                long amount = parsePositiveAmount(amountInput);
                if (amount <= 0L) {
                    setStatus("Enter a valid positive amount.", "#ff6666");
                    return;
                }
                long updated = economy.deposit(targetId, amount);
                audit.log("ADMIN_GIVE", actor, targetName, amount, updated, "ecoadmin");
                Messages.sendPrefixedKey(playerRef, "economy.money.give", Map.of(
                        "player", targetName,
                        "amount", economy.formatAmount(amount)
                ));
                PlayerRef target = Universe.get().getPlayer(targetId);
                if (target != null) {
                    Messages.sendPrefixedKey(target, "economy.money.give_target", Map.of(
                            "player", actor,
                            "amount", economy.formatAmount(amount)
                    ));
                    hudManager.refreshPlayer(target);
                }
                setStatus("Added " + economy.formatAmount(amount) + " to " + targetName + ".", "#55ff55");
            }
            case "Take" -> {
                long amount = parsePositiveAmount(amountInput);
                if (amount <= 0L) {
                    setStatus("Enter a valid positive amount.", "#ff6666");
                    return;
                }
                if (!economy.withdraw(targetId, amount)) {
                    setStatus(targetName + " has insufficient funds.", "#ff6666");
                    return;
                }
                long updated = economy.getBalance(targetId);
                audit.log("ADMIN_TAKE", actor, targetName, amount, updated, "ecoadmin");
                Messages.sendPrefixedKey(playerRef, "economy.money.take", Map.of(
                        "player", targetName,
                        "amount", economy.formatAmount(amount)
                ));
                PlayerRef target = Universe.get().getPlayer(targetId);
                if (target != null) {
                    Messages.sendPrefixedKey(target, "economy.money.take_target", Map.of(
                            "player", actor,
                            "amount", economy.formatAmount(amount)
                    ));
                    hudManager.refreshPlayer(target);
                }
                setStatus("Removed " + economy.formatAmount(amount) + " from " + targetName + ".", "#55ff55");
            }
            case "Set" -> {
                long amount = parseNonNegativeAmount(amountInput);
                if (amount < 0L) {
                    setStatus("Enter a valid amount.", "#ff6666");
                    return;
                }
                long updated = economy.setBalance(targetId, amount);
                audit.log("ADMIN_SET", actor, targetName, updated, updated, "ecoadmin");
                Messages.sendPrefixedKey(playerRef, "economy.money.set", Map.of(
                        "player", targetName,
                        "amount", economy.formatAmount(updated)
                ));
                PlayerRef target = Universe.get().getPlayer(targetId);
                if (target != null) {
                    Messages.sendPrefixedKey(target, "economy.money.set_target", Map.of(
                            "player", actor,
                            "amount", economy.formatAmount(updated)
                    ));
                    hudManager.refreshPlayer(target);
                }
                setStatus("Set " + targetName + " to " + economy.formatAmount(updated) + ".", "#55ff55");
            }
            case "Reset" -> {
                long updated = economy.setBalance(targetId, config.getEconomyStartingBalance());
                audit.log("ADMIN_RESET", actor, targetName, updated, updated, "ecoadmin");
                Messages.sendPrefixedKey(playerRef, "economy.money.reset", Map.of(
                        "player", targetName,
                        "amount", economy.formatAmount(updated)
                ));
                PlayerRef target = Universe.get().getPlayer(targetId);
                if (target != null) {
                    Messages.sendPrefixedKey(target, "economy.money.set_target", Map.of(
                            "player", actor,
                            "amount", economy.formatAmount(updated)
                    ));
                    hudManager.refreshPlayer(target);
                }
                setStatus("Reset " + targetName + " balance.", "#55ff55");
            }
            default -> {
            }
        }
    }

    private void applyConfigInputs() {
        String symbol = cfgCurrencySymbol == null ? "" : cfgCurrencySymbol.trim();
        if (!symbol.isBlank()) {
            config.setEconomyCurrencySymbol(symbol);
        }
        long starting = economy.parseAmount(cfgStartingBalance);
        if (starting >= 0L) {
            config.setEconomyStartingBalance(starting);
        }
        String label = cfgHudLabel == null ? "" : cfgHudLabel.trim();
        if (!label.isBlank()) {
            config.setEconomyHudLabel(label);
        }
        Integer interval = parseOptionalPositiveInt(cfgHudInterval);
        if (interval != null) {
            config.setEconomyHudUpdateIntervalMs(interval);
        }
        config.setEconomyHudEnabled(cfgHudEnabled);
        config.setEconomyHudDefaultHidden(cfgHudDefaultHidden);
        config.setEconomyHudAnchor(cfgHudAnchor);
        syncConfigDraftFromConfig();
    }

    private void syncConfigDraftFromConfig() {
        cfgCurrencySymbol = config.getEconomyCurrencySymbol();
        cfgStartingBalance = economy.formatAmountRaw(config.getEconomyStartingBalance());
        cfgHudLabel = config.getEconomyHudLabel();
        cfgHudInterval = String.valueOf(config.getEconomyHudUpdateIntervalMs());
        cfgHudEnabled = config.isEconomyHudEnabled();
        cfgHudDefaultHidden = config.isEconomyHudDefaultHidden();
        cfgHudAnchor = config.getEconomyHudAnchor();
    }

    private void forceSaveData() {
        for (UUID id : collectPlayerIds()) {
            PlayerDataModel data = storage.getPlayerData(id);
            storage.savePlayerDataAsync(id, data);
        }
        audit.forceSave();
    }

    @Nonnull
    private Set<UUID> collectPlayerIds() {
        Set<UUID> ids = new HashSet<>(storage.listPlayerIds());
        for (PlayerRef online : Universe.get().getPlayers()) {
            if (online != null) {
                ids.add(online.getUuid());
            }
        }
        return ids;
    }

    @Nonnull
    private List<PlayerRow> collectPlayerRows(@Nonnull String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<PlayerRow> rows = new ArrayList<>();
        for (UUID id : collectPlayerIds()) {
            PlayerRef online = Universe.get().getPlayer(id);
            String name = resolveDisplayName(id, online != null ? online.getUsername() : id.toString());
            if (!normalized.isBlank() && !name.toLowerCase(Locale.ROOT).contains(normalized)) {
                continue;
            }
            rows.add(new PlayerRow(id, name, economy.getBalance(id)));
        }
        rows.sort(Comparator.comparing((PlayerRow row) -> row.name.toLowerCase(Locale.ROOT)));
        return rows;
    }

    @Nonnull
    private String resolveDisplayName(@Nonnull UUID id, @Nonnull String fallback) {
        PlayerRef online = Universe.get().getPlayer(id);
        if (online != null && online.getUsername() != null && !online.getUsername().isBlank()) {
            return online.getUsername();
        }
        PlayerDataModel data = storage.getPlayerData(id);
        String name = data.getLastKnownName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return fallback;
    }

    @Nonnull
    private String formatTime(long epochMs) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(Math.max(0L, epochMs)).atZone(ZoneId.systemDefault()));
    }

    private long parsePositiveAmount(@Nullable String raw) {
        long parsed = economy.parseAmount(raw);
        if (parsed <= 0L) {
            return -1L;
        }
        return parsed;
    }

    private long parseNonNegativeAmount(@Nullable String raw) {
        return economy.parseAmount(raw);
    }

    @Nullable
    private Integer parseOptionalPositiveInt(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().replace(",", "");
        if (normalized.isBlank() || !normalized.matches("\\d+")) {
            return null;
        }
        try {
            int value = Integer.parseInt(normalized);
            return Math.max(1, value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void setStatus(@Nonnull String message, @Nonnull String color) {
        statusMessage = message;
        statusColor = color;
    }

    @Nonnull
    private String nextAnchor(@Nonnull String anchor) {
        return switch (anchor) {
            case "top_left" -> "top_right";
            case "top_right" -> "bottom_right";
            case "bottom_right" -> "bottom_left";
            default -> "top_left";
        };
    }

    @Nonnull
    private String prettyAnchor(@Nonnull String anchor) {
        return anchor.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    @Nonnull
    private String sanitizeName(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return "-";
        }
        return input.replace("\n", " ").replace("\r", " ");
    }

    @Nonnull
    private String escapeInline(@Nonnull String text) {
        return text.replace("\\", "\\\\").replace("\"", "'");
    }

    @Nonnull
    private Tab parseTab(@Nonnull String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "players" -> Tab.PLAYERS;
            case "top" -> Tab.TOP;
            case "log" -> Tab.LOG;
            default -> Tab.DASHBOARD;
        };
    }

    private static final class PlayerRow {
        private final UUID uuid;
        private final String name;
        private final long balance;

        private PlayerRow(@Nonnull UUID uuid, @Nonnull String name, long balance) {
            this.uuid = uuid;
            this.name = name;
            this.balance = Math.max(0L, balance);
        }
    }

    private enum Tab {
        DASHBOARD,
        PLAYERS,
        TOP,
        LOG
    }

    public static final class AdminEventData {
        public static final BuilderCodec<AdminEventData> CODEC = BuilderCodec
                .builder(AdminEventData.class, AdminEventData::new)
                .append(new KeyedCodec<>("Tab", Codec.STRING), (d, v) -> d.tab = v, d -> d.tab).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("PlayerAction", Codec.STRING), (d, v) -> d.playerAction = v, d -> d.playerAction).add()
                .append(new KeyedCodec<>("PlayerUuid", Codec.STRING), (d, v) -> d.playerUuid = v, d -> d.playerUuid).add()
                .append(new KeyedCodec<>("PlayerName", Codec.STRING), (d, v) -> d.playerName = v, d -> d.playerName).add()
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery).add()
                .append(new KeyedCodec<>("@LogFilter", Codec.STRING), (d, v) -> d.logFilter = v, d -> d.logFilter).add()
                .append(new KeyedCodec<>("@AmountInput", Codec.STRING), (d, v) -> d.amountInput = v, d -> d.amountInput).add()
                .append(new KeyedCodec<>("@ConfigSymbol", Codec.STRING), (d, v) -> d.cfgSymbol = v, d -> d.cfgSymbol).add()
                .append(new KeyedCodec<>("@ConfigStarting", Codec.STRING), (d, v) -> d.cfgStarting = v, d -> d.cfgStarting).add()
                .append(new KeyedCodec<>("@ConfigHudLabel", Codec.STRING), (d, v) -> d.cfgHudLabel = v, d -> d.cfgHudLabel).add()
                .append(new KeyedCodec<>("@ConfigHudInterval", Codec.STRING), (d, v) -> d.cfgHudInterval = v, d -> d.cfgHudInterval).add()
                .build();

        private String tab;
        private String action;
        private String playerAction;
        private String playerUuid;
        private String playerName;
        private String searchQuery;
        private String logFilter;
        private String amountInput;
        private String cfgSymbol;
        private String cfgStarting;
        private String cfgHudLabel;
        private String cfgHudInterval;
    }
}
