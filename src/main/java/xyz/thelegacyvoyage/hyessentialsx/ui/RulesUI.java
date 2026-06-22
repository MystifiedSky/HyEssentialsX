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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class RulesUI extends com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage {

    private static final String LAYOUT = "hyessentialsx/RulesPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/RulesRow.ui";

    private final PlayerRef playerRef;
    private final ConfigManager config;
    private final Gson gson = new Gson();

    public RulesUI(@Nonnull PlayerRef playerRef, @Nonnull ConfigManager config) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.playerRef = playerRef;
        this.config = config;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        List<String> rules = config.getRules();
        cmd.set("#RuleCount.Text", rules.size() + " Rules");
        buildRulesList(cmd, rules);

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

    private void buildRulesList(@Nonnull UICommandBuilder cmd, @Nonnull List<String> rules) {
        cmd.clear("#RuleList");

        if (rules.isEmpty()) {
            cmd.appendInline("#RuleList",
                    "Label { Text: \"No rules set.\"; " +
                            "Style: (FontSize: 13, TextColor: #666666, HorizontalAlignment: Center); " +
                            "Anchor: (Top: 30); }");
            return;
        }

        for (int i = 0; i < rules.size(); i++) {
            String rule = rules.get(i);
            cmd.append("#RuleList", ROW_LAYOUT);
            String selector = "#RuleList[" + i + "]";
            cmd.set(selector + ".TextSpans", PlaceholderApiUtil.apply(playerRef, rule));
        }
    }
}

