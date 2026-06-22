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
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class WarpsUI extends com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage {

    private static final String LAYOUT = "hyessentialsx/WarpPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/WarpRow.ui";
    private static final String BYPASS_PERMISSION = "hyessentialsx.warp.bypass";

    private final PlayerRef playerRef;
    private final WarpManager warpManager;
    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final Gson gson = new Gson();

    public WarpsUI(@Nonnull PlayerRef playerRef,
                   @Nonnull WarpManager warpManager,
                   @Nonnull TPManager tpManager,
                   @Nonnull ConfigManager config,
                   @Nonnull CommandCooldownManager cooldowns) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.playerRef = playerRef;
        this.warpManager = warpManager;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        List<String> warps = warpManager.listWarps();
        cmd.set("#WarpCount.Text", warps.size() + " Warps");

        buildWarpsList(cmd, evt, warps);

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
            return;
        }

        if (action.startsWith("warp:")) {
            String name = action.substring("warp:".length());
            teleportToWarp(ref, store, name);
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void buildWarpsList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull List<String> warps) {
        cmd.clear("#WarpList");

        if (warps.isEmpty()) {
            cmd.appendInline("#WarpList",
                    "Label { Text: \"No warps set.\"; " +
                            "Style: (FontSize: 13, TextColor: #666666, HorizontalAlignment: Center); " +
                            "Anchor: (Top: 30); }");
            return;
        }

        for (int i = 0; i < warps.size(); i++) {
            String name = warps.get(i);
            cmd.append("#WarpList", ROW_LAYOUT);
            String selector = "#WarpList[" + i + "]";
            cmd.set(selector + ".Text", name);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("action", "warp:" + name),
                    false
            );
        }
    }

    private void teleportToWarp(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String name) {
        if (name.isEmpty()) {
            return;
        }
        if (!cooldowns.canUse(playerRef, CooldownKeys.WARP, "/warp", BYPASS_PERMISSION)) {
            return;
        }

        WarpModel warp = warpManager.getWarp(name);
        if (warp == null) {
            Messages.sendPrefixedKey(playerRef, "warp.not_found", Map.of());
            return;
        }

        close();

        int warmupSeconds = config.getWarpWarmupSeconds();
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(playerRef.getUuid())) {
                Messages.sendPrefixedKey(playerRef, "teleport.pending", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
            if (transform == null || transform.getPosition() == null) {
                Messages.sendPrefixedKey(playerRef, "teleport.position_unavailable", Map.of());
                return;
            }
            tpManager.queue(
                    playerRef.getUuid(),
                    transform.getPosition().clone(),
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                ref,
                                warp.getWorldName(),
                                warp.getX(), warp.getY(), warp.getZ(),
                                warp.getYaw(), warp.getPitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(playerRef, err);
                            return;
                        }
                        cooldowns.apply(playerRef, CooldownKeys.WARP);
                        Messages.sendPrefixedKey(playerRef, "teleport.success.warp", Map.of("warp", name));
                    }
            );
            Messages.sendPrefixedKey(playerRef, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                warp.getWorldName(),
                warp.getX(), warp.getY(), warp.getZ(),
                warp.getYaw(), warp.getPitch()
        );
        if (err != null) {
            Messages.sendPrefixed(playerRef, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.WARP);
        Messages.sendPrefixedKey(playerRef, "teleport.success.warp", Map.of("warp", name));
    }
}
