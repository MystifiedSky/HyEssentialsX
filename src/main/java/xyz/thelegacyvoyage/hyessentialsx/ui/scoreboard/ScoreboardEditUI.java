package xyz.thelegacyvoyage.hyessentialsx.ui.scoreboard;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.ScoreboardManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ScoreboardEditUI extends InteractiveCustomUIPage<ScoreboardEditEventData> {

    private final ConfigManager config;
    private final ScoreboardManager scoreboardManager;
    private int editingLineIndex = -1;

    public ScoreboardEditUI(@Nonnull PlayerRef playerRef,
                            @Nonnull ConfigManager config,
                            @Nonnull ScoreboardManager scoreboardManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, ScoreboardEditEventData.CODEC);
        this.config = config;
        this.scoreboardManager = scoreboardManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        commandBuilder.append("hyessentialsx/ScoreboardEdit.ui");
        buildLinesList(commandBuilder, eventBuilder);
        commandBuilder.set("#EditModeLabel.Visible", false);
        commandBuilder.set("#AddLineButton.Visible", true);
        commandBuilder.set("#UpdateLineButton.Visible", false);
        commandBuilder.set("#CancelEditButton.Visible", false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddLineButton",
                new EventData().append("Action", "addLine").append("@NewText", "#NewLineInput.Value"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#UpdateLineButton",
                new EventData().append("Action", "updateLine").append("@NewText", "#NewLineInput.Value"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CancelEditButton", EventData.of("Action", "cancelEdit"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ScoreboardEditEventData data) {
        String action = data.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case "addLine" -> {
                String text = safeText(data.getNewLineText());
                if (!text.isBlank()) {
                    List<String> lines = new ArrayList<>(config.getScoreboardLines());
                    lines.add(text);
                    config.setScoreboardLines(lines);
                    scoreboardManager.refreshAll();
                }
                editingLineIndex = -1;
                refreshUI(ref, store);
            }
            case "removeLine" -> {
                int index = data.getLineIndexInt();
                List<String> lines = new ArrayList<>(config.getScoreboardLines());
                if (index >= 0 && index < lines.size()) {
                    lines.remove(index);
                    config.setScoreboardLines(lines);
                    scoreboardManager.refreshAll();
                }
                editingLineIndex = -1;
                refreshUI(ref, store);
            }
            case "editLine" -> {
                int index = data.getLineIndexInt();
                List<String> lines = config.getScoreboardLines();
                if (index >= 0 && index < lines.size()) {
                    editingLineIndex = index;
                    enterEditMode(lines.get(index));
                }
            }
            case "updateLine" -> {
                String text = safeText(data.getNewLineText());
                List<String> lines = new ArrayList<>(config.getScoreboardLines());
                if (editingLineIndex >= 0 && editingLineIndex < lines.size() && !text.isBlank()) {
                    lines.set(editingLineIndex, text);
                    config.setScoreboardLines(lines);
                    scoreboardManager.refreshAll();
                }
                editingLineIndex = -1;
                refreshUI(ref, store);
            }
            case "cancelEdit" -> {
                editingLineIndex = -1;
                refreshUI(ref, store);
            }
            case "close" -> closePage(ref, store);
            default -> {
            }
        }
    }

    private void refreshUI(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder builder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        builder.set("#NewLineInput.Value", "");
        builder.set("#EditModeLabel.Visible", false);
        builder.set("#AddLineButton.Visible", true);
        builder.set("#UpdateLineButton.Visible", false);
        builder.set("#CancelEditButton.Visible", false);
        buildLinesList(builder, eventBuilder);
        sendUpdate(builder, eventBuilder, false);
    }

    private void enterEditMode(@Nonnull String lineText) {
        UICommandBuilder builder = new UICommandBuilder();
        builder.set("#NewLineInput.Value", lineText);
        builder.set("#EditModeLabel.Text", "Editing line " + (editingLineIndex + 1) + ":");
        builder.set("#EditModeLabel.Visible", true);
        builder.set("#AddLineButton.Visible", false);
        builder.set("#UpdateLineButton.Visible", true);
        builder.set("#CancelEditButton.Visible", true);
        sendUpdate(builder, null, false);
    }

    private void buildLinesList(@Nonnull UICommandBuilder builder, @Nonnull UIEventBuilder eventBuilder) {
        builder.clear("#LinesList");
        List<String> lines = config.getScoreboardLines();
        if (lines.isEmpty()) {
            builder.appendInline("#LinesList", "Label { Text: \"No lines yet.\"; Style: (Alignment: Center, TextColor: #6e7da1); }");
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            builder.append("#LinesList", "hyessentialsx/ScoreboardEditLineItem.ui");
            String selector = "#LinesList[" + i + "]";
            builder.set(selector + " #LineNumber.Text", (i + 1) + ".");
            builder.set(selector + " #LineText.Text", lines.get(i));
            builder.set(selector + " #DeleteLabel.Text", "X");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    selector + " #LineButton",
                    EventData.of("Action", "editLine").append("LineIndex", String.valueOf(i)),
                    false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    selector + " #LineDeleteButton",
                    EventData.of("Action", "removeLine").append("LineIndex", String.valueOf(i)),
                    false);
        }
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private void closePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }
}
