package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.google.gson.Gson;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TpaUI extends com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage {

    private static final String LAYOUT = "hyessentialsx/TpaPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/TpaRow.ui";
    private static final String PERMISSION_NODE = "hyessentialsx.tpa";
    private static final String BYPASS_PERMISSION = "hyessentialsx.tpa.bypass";

    private final PlayerRef playerRef;
    private final TPManager tpManager;
    private final CommandCooldownManager cooldowns;
    private final ConfigManager config;
    private final String initialQuery;
    private final Gson gson = new Gson();

    public TpaUI(@Nonnull PlayerRef playerRef,
                 @Nonnull TPManager tpManager,
                 @Nonnull CommandCooldownManager cooldowns,
                 @Nonnull ConfigManager config,
                 String initialQuery) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.playerRef = playerRef;
        this.tpManager = tpManager;
        this.cooldowns = cooldowns;
        this.config = config;
        this.initialQuery = initialQuery;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);
        String query = initialQuery == null ? "" : initialQuery;
        cmd.set("#SearchInput.Value", query);

        List<PlayerRef> players = collectPlayers(query);
        cmd.set("#PlayerCount.Text", Messages.tr(playerRef, "tpa.ui.player_count",
                Map.of("count", String.valueOf(players.size()))));
        buildPlayersList(cmd, evt, players);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("action", "close"),
                false
        );
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SearchButton",
                EventData.of("action", "search").append("query", "@SearchInput.Value"),
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
            return;
        }

        if (action.equals("search")) {
            String query = extractQuery(payload);
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            TpaUI page = new TpaUI(playerRef, tpManager, cooldowns, config, query);
            player.getPageManager().openCustomPage(ref, store, page);
            return;
        }

        if (action.startsWith("tpa:")) {
            String id = action.substring("tpa:".length());
            handleTpa(ref, id);
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private List<PlayerRef> collectPlayers(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<PlayerRef> out = new ArrayList<>();
        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (ref == null || ref.getUuid().equals(playerRef.getUuid())) {
                continue;
            }
            String name = ref.getUsername();
            if (name == null) continue;
            if (!normalized.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(normalized)) {
                continue;
            }
            out.add(ref);
        }
        return out;
    }

    private void buildPlayersList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull List<PlayerRef> players) {
        cmd.clear("#PlayerList");

        if (players.isEmpty()) {
            cmd.appendInline("#PlayerList",
                    "Label { Text: \"" + Messages.tr(playerRef, "tpa.ui.no_players", Map.of()) + "\"; " +
                            "Style: (FontSize: 13, TextColor: #666666, HorizontalAlignment: Center); " +
                            "Anchor: (Top: 30); }");
            return;
        }

        for (int i = 0; i < players.size(); i++) {
            PlayerRef target = players.get(i);
            cmd.append("#PlayerList", ROW_LAYOUT);
            String selector = "#PlayerList[" + i + "]";
            cmd.set(selector + ".Text", target.getUsername());
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("action", "tpa:" + target.getUuid()),
                    false
            );
        }
    }

    private void handleTpa(@Nonnull Ref<EntityStore> ref, @Nonnull String uuidText) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch (Exception e) {
            return;
        }
        PlayerRef target = Universe.get().getPlayer(uuid);
        if (target == null) {
            Messages.sendPrefixedKey(playerRef, "player.not_found", Map.of());
            return;
        }
        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.sendPrefixedKey(playerRef, "tpa.self", Map.of());
            return;
        }
        if (!CommandPermissionUtil.hasPermission(playerRef, PERMISSION_NODE)) {
            Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of("command", "/tpa"));
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.sendPrefixedKey(playerRef, "tpa.disabled", Map.of());
            return;
        }
        if (!cooldowns.canUse(playerRef, CooldownKeys.TPA, "/tpa", BYPASS_PERMISSION)) {
            return;
        }
        if (tpManager.isTpaIgnored(target.getUuid())) {
            Messages.sendPrefixedKey(playerRef, "tpa.target_ignored", Map.of("player", target.getUsername()));
            return;
        }
        boolean created = tpManager.addTpaRequest(playerRef.getUuid(), target.getUuid());
        if (!created) {
            Messages.sendPrefixedKey(playerRef, "tpa.request.pending", Map.of("player", target.getUsername()));
            return;
        }
        Messages.sendPrefixedKey(playerRef, "tpa.request.sent", Map.of("player", target.getUsername()));
        Messages.sendKey(target, "tpa.request.received", Map.of("player", playerRef.getUsername()));
        cooldowns.apply(playerRef, CooldownKeys.TPA);
        close();
    }

    private String extractQuery(@Nonnull Map<?, ?> payload) {
        Object value = payload.get("query");
        if (!(value instanceof String)) {
            return "";
        }
        return ((String) value).trim();
    }
}

