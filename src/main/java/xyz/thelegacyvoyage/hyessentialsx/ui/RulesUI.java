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
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;

import javax.annotation.Nonnull;
import java.util.List;

public final class RulesUI extends InteractiveCustomUIPage<RulesUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/RulesPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/RulesRow.ui";
    private static final int RULES_PER_PAGE = 8;

    private final PlayerRef playerRef;
    private final ConfigManager config;
    private final int requestedPage;
    private int currentPage;

    public RulesUI(@Nonnull PlayerRef playerRef, @Nonnull ConfigManager config) {
        this(playerRef, config, 0);
    }

    public RulesUI(@Nonnull PlayerRef playerRef,
                   @Nonnull ConfigManager config,
                   int page) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.config = config;
        this.requestedPage = Math.max(0, page);
        this.currentPage = this.requestedPage;
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
        int totalRules = rules.size();
        int totalPages = getTotalPages(totalRules);
        currentPage = Math.min(requestedPage, totalPages - 1);
        int start = currentPage * RULES_PER_PAGE;
        int end = Math.min(totalRules, start + RULES_PER_PAGE);

        cmd.set("#RuleCount.Text", totalRules + (totalRules == 1 ? " Rule" : " Rules"));
        cmd.set("#PageInfo.Text", "Page " + (currentPage + 1) + "/" + totalPages);
        cmd.set("#RangeInfo.Text", buildRangeText(totalRules, start, end));
        cmd.set("#PrevButton.Disabled", currentPage <= 0);
        cmd.set("#NextButton.Disabled", currentPage >= totalPages - 1);
        buildRulesList(cmd, rules, start, end);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "Close"),
                false
        );
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PrevButton",
                EventData.of("Action", "Prev"),
                false
        );
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NextButton",
                EventData.of("Action", "Next"),
                false
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UIEventData data
    ) {
        if (data.action == null || data.action.isEmpty()) {
            return;
        }

        switch (data.action) {
            case "Close" -> close();
            case "Prev" -> {
                if (currentPage > 0) {
                    reopenWithPage(ref, store, currentPage - 1);
                }
            }
            case "Next" -> {
                int totalPages = getTotalPages(config.getRules().size());
                if (currentPage + 1 < totalPages) {
                    reopenWithPage(ref, store, currentPage + 1);
                }
            }
            default -> {
            }
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void buildRulesList(@Nonnull UICommandBuilder cmd,
                                @Nonnull List<String> rules,
                                int start,
                                int end) {
        cmd.clear("#RuleList");

        if (rules.isEmpty()) {
            cmd.appendInline("#RuleList",
                    "Label { Text: \"No rules set.\"; " +
                            "Style: (FontSize: 13, TextColor: #666666, HorizontalAlignment: Center, RenderBold: true); " +
                            "Anchor: (Top: 30); }");
            return;
        }

        int row = 0;
        for (int i = start; i < end; i++) {
            String rule = rules.get(i);
            cmd.append("#RuleList", ROW_LAYOUT);
            String selector = "#RuleList[" + row + "]";
            cmd.set(selector + " #RuleIndex.Text", "Rule #" + (i + 1));
            cmd.set(selector + " #RuleText.TextSpans", PlaceholderApiUtil.apply(playerRef, rule));
            row++;
        }
    }

    private void reopenWithPage(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                int page) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        RulesUI nextPage = new RulesUI(playerRef, config, page);
        player.getPageManager().openCustomPage(ref, store, nextPage);
    }

    private int getTotalPages(int totalRules) {
        if (totalRules <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(totalRules / (double) RULES_PER_PAGE));
    }

    @Nonnull
    private String buildRangeText(int totalRules, int start, int end) {
        if (totalRules <= 0) {
            return "Showing 0 of 0";
        }
        return "Showing " + (start + 1) + "-" + end + " of " + totalRules;
    }

    public static final class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec
                .builder(UIEventData.class, UIEventData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .build();

        private String action;
    }
}
