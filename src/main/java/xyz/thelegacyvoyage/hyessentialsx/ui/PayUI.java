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
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyAuditManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PayUI extends InteractiveCustomUIPage<PayUI.PayUIEventData> {

    private static final String LAYOUT = "hyessentialsx/PayPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/PayPlayerEntry.ui";
    private static final String PERMISSION_NODE = "hyessentialsx.pay";

    private final PlayerRef playerRef;
    private final EconomyManager economy;
    private final EconomyAuditManager audit;

    private String searchQuery = "";
    private String selectedPlayerUuid;
    private String amountInput = "";

    public PayUI(@Nonnull PlayerRef playerRef,
                 @Nonnull EconomyManager economy,
                 @Nonnull EconomyAuditManager audit) {
        super(playerRef, CustomPageLifetime.CanDismiss, PayUIEventData.CODEC);
        this.playerRef = playerRef;
        this.economy = economy;
        this.audit = audit;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        bindStaticEvents(events);
        rebuild(cmd, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PayUIEventData data) {
        if (data.searchQuery != null) {
            searchQuery = data.searchQuery.trim().toLowerCase(Locale.ROOT);
            refresh();
            return;
        }
        if (data.amountInput != null) {
            amountInput = data.amountInput.trim();
            refresh();
            return;
        }
        if (data.selectedPlayer != null) {
            selectedPlayerUuid = data.selectedPlayer;
            refresh();
            return;
        }
        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "Close" -> close();
            case "Confirm" -> handleConfirm();
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
        rebuild(cmd, events);
        sendUpdate(cmd, events, false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.set("#BalanceValue.Text", economy.formatAmount(economy.getBalance(playerRef.getUuid())));
        cmd.set("#Subtitle.Text", "Pay " + resolveCurrencyName() + " to another player");
        cmd.set("#SearchInput.Value", searchQuery);
        cmd.set("#AmountInput.Value", amountInput);
        buildPlayerList(cmd, events);
        updatePreview(cmd);
    }

    private void bindStaticEvents(@Nonnull UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
                EventData.of("@SearchQuery", "#SearchInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AmountInput",
                EventData.of("@AmountInput", "#AmountInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", "Close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton",
                EventData.of("Action", "Confirm"), false);
    }

    private void buildPlayerList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        cmd.clear("#PlayerList");
        List<PlayerRef> players = collectPlayers();
        if (players.isEmpty()) {
            cmd.appendInline("#PlayerList",
                    "Group { LayoutMode: Center; Anchor: (Height: 80); " +
                            "Label { Text: \"No players online\"; " +
                            "Style: (FontSize: 14, TextColor: #888888, HorizontalAlignment: Center); } }");
            return;
        }
        int index = 0;
        for (PlayerRef target : players) {
            String uuid = target.getUuid().toString();
            cmd.append("#PlayerList", ROW_LAYOUT);
            String row = "#PlayerList[" + index + "]";
            cmd.set(row + " #PlayerName.Text", target.getUsername());
            cmd.set(row + " #SelectedIcon.Visible", uuid.equals(selectedPlayerUuid));
            events.addEventBinding(CustomUIEventBindingType.Activating, row,
                    EventData.of("SelectedPlayer", uuid), false);
            index++;
        }
    }

    @Nonnull
    private List<PlayerRef> collectPlayers() {
        List<PlayerRef> out = new ArrayList<>();
        for (PlayerRef target : Universe.get().getPlayers()) {
            if (target == null || target.getUuid().equals(playerRef.getUuid())) {
                continue;
            }
            String name = target.getUsername();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!searchQuery.isBlank() && !name.toLowerCase(Locale.ROOT).contains(searchQuery)) {
                continue;
            }
            out.add(target);
        }
        return out;
    }

    private void updatePreview(@Nonnull UICommandBuilder cmd) {
        long amount = parseAmount(amountInput);
        long fee = 0L;
        long balance = economy.getBalance(playerRef.getUuid());
        long after = amount > 0L ? balance - amount - fee : balance;
        cmd.set("#FeeValue.Text", economy.formatAmount(fee));
        if (amount <= 0L) {
            cmd.set("#BalanceAfterValue.Text", economy.formatAmount(balance));
            return;
        }
        if (after < 0L) {
            cmd.set("#BalanceAfterValue.Text", "Insufficient");
        } else {
            cmd.set("#BalanceAfterValue.Text", economy.formatAmount(after));
        }
    }

    private void handleConfirm() {
        if (!CommandPermissionUtil.hasPermission(playerRef, PERMISSION_NODE)) {
            Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of("command", "/pay"));
            return;
        }
        if (selectedPlayerUuid == null || selectedPlayerUuid.isBlank()) {
            Messages.sendPrefixedKey(playerRef, "economy.pay.select_player", Map.of());
            return;
        }
        long amount = parseAmount(amountInput);
        if (amount <= 0L) {
            Messages.sendPrefixedKey(playerRef, "economy.invalid_amount", Map.of());
            return;
        }

        UUID targetId;
        try {
            targetId = UUID.fromString(selectedPlayerUuid);
        } catch (IllegalArgumentException ex) {
            Messages.sendPrefixedKey(playerRef, "player.not_found", Map.of());
            return;
        }

        PlayerRef target = Universe.get().getPlayer(targetId);
        if (target == null) {
            Messages.sendPrefixedKey(playerRef, "player.not_found", Map.of());
            return;
        }
        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.sendPrefixedKey(playerRef, "economy.pay.self", Map.of());
            return;
        }

        long balance = economy.getBalance(playerRef.getUuid());
        if (balance < amount) {
            Messages.sendPrefixedKey(playerRef, "economy.insufficient_funds", Map.of());
            return;
        }

        if (!economy.withdraw(playerRef.getUuid(), amount)) {
            Messages.sendPrefixedKey(playerRef, "economy.insufficient_funds", Map.of());
            return;
        }
        economy.deposit(target.getUuid(), amount);

        String formatted = economy.formatAmount(amount);
        Messages.sendPrefixedKey(playerRef, "economy.pay.sent", Map.of(
                "player", target.getUsername(),
                "amount", formatted
        ));
        Messages.sendPrefixedKey(target, "economy.pay.received", Map.of(
                "player", playerRef.getUsername(),
                "amount", formatted
        ));
        audit.log(
                "PAY",
                playerRef.getUsername(),
                target.getUsername(),
                amount,
                economy.getBalance(playerRef.getUuid()),
                "gui"
        );
        close();
    }

    private long parseAmount(@Nullable String raw) {
        return economy.parseAmount(raw);
    }

    @Nonnull
    private String resolveCurrencyName() {
        String label = economy.getCurrencyName();
        if (label == null) {
            return "currency";
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return "currency";
        }
        return trimmed;
    }

    public static final class PayUIEventData {
        public static final BuilderCodec<PayUIEventData> CODEC = BuilderCodec
                .builder(PayUIEventData.class, PayUIEventData::new)
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (d, v) -> d.searchQuery = v, d -> d.searchQuery).add()
                .append(new KeyedCodec<>("@AmountInput", Codec.STRING), (d, v) -> d.amountInput = v, d -> d.amountInput).add()
                .append(new KeyedCodec<>("SelectedPlayer", Codec.STRING), (d, v) -> d.selectedPlayer = v, d -> d.selectedPlayer).add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .build();

        private String searchQuery;
        private String amountInput;
        private String selectedPlayer;
        private String action;
    }
}
