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

public class ScoreboardAdminMoveUI extends InteractiveCustomUIPage<ScoreboardMoveEventData> {

    private static final int OFFSET_STEP = 5;
    private final ConfigManager config;
    private final ScoreboardManager scoreboardManager;
    private final PlayerRef playerRef;

    public ScoreboardAdminMoveUI(@Nonnull PlayerRef playerRef,
                                 @Nonnull ConfigManager config,
                                 @Nonnull ScoreboardManager scoreboardManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, ScoreboardMoveEventData.CODEC);
        this.playerRef = playerRef;
        this.config = config;
        this.scoreboardManager = scoreboardManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        scoreboardManager.enableDefaultOffsetPreview(playerRef.getUuid());
        scoreboardManager.refreshPlayer(playerRef);
        commandBuilder.append("hyessentialsx/ScoreboardMove.ui");
        commandBuilder.set("#OffsetText.Text", formatOffsetText());

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#UpButton", EventData.of("Action", "up"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DownButton", EventData.of("Action", "down"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#LeftButton", EventData.of("Action", "left"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RightButton", EventData.of("Action", "right"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton", EventData.of("Action", "reset"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ScoreboardMoveEventData data) {
        String action = data.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case "up" -> applyMove(0, -OFFSET_STEP);
            case "down" -> applyMove(0, OFFSET_STEP);
            case "left" -> applyMove(-OFFSET_STEP, 0);
            case "right" -> applyMove(OFFSET_STEP, 0);
            case "reset" -> resetOffsets();
            case "close" -> closePage(ref, store);
            default -> {
            }
        }
    }

    private void applyMove(int deltaX, int deltaY) {
        String anchor = config.getScoreboardAnchor();
        boolean rightAnchor = anchor.contains("right");
        boolean topAnchor = anchor.contains("top");
        int adjustedX = rightAnchor ? -deltaX : deltaX;
        int adjustedY = topAnchor ? deltaY : -deltaY;
        scoreboardManager.adjustDefaultOffsets(adjustedX, adjustedY);
        updateOffsetLabel();
    }

    private void resetOffsets() {
        scoreboardManager.setDefaultOffsets(0, 0);
        updateOffsetLabel();
    }

    private void updateOffsetLabel() {
        UICommandBuilder builder = new UICommandBuilder();
        builder.set("#OffsetText.Text", formatOffsetText());
        sendUpdate(builder, null, false);
    }

    private String formatOffsetText() {
        return "Default Offset: " + config.getScoreboardOffsetX() + ", " + config.getScoreboardOffsetY();
    }

    private void closePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        scoreboardManager.disableDefaultOffsetPreview(playerRef.getUuid());
        scoreboardManager.refreshPlayer(playerRef);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        scoreboardManager.disableDefaultOffsetPreview(playerRef.getUuid());
        scoreboardManager.refreshPlayer(playerRef);
    }
}
