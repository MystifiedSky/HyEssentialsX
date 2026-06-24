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
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlayerWarpManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerWarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerWarpsUI extends InteractiveCustomUIPage<PlayerWarpsUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/WarpPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/WarpRow.ui";

    private final PlayerRef playerRef;
    private final PlayerWarpManager playerWarps;
    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final BackManager backManager;
    @Nullable
    private final EconomyManager economy;

    public PlayerWarpsUI(@Nonnull PlayerRef playerRef,
                         @Nonnull PlayerWarpManager playerWarps,
                         @Nonnull TPManager tpManager,
                         @Nonnull ConfigManager config,
                         @Nonnull CommandCooldownManager cooldowns,
                         @Nonnull BackManager backManager,
                         @Nullable EconomyManager economy) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.playerWarps = playerWarps;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.backManager = backManager;
        this.economy = economy;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        List<PlayerWarpModel> warps = playerWarps.listVisibleWarps(playerRef.getUuid(),
                CommandPermissionUtil.hasPermission(playerRef, "hyessentialsx.playerwarp.admin"), "");
        cmd.set("#WarpCount.Text", warps.size() + " Player Warps");
        cmd.clear("#WarpList");
        if (warps.isEmpty()) {
            cmd.appendInline("#WarpList", "Label { Text: \"No public player warps yet.\"; Anchor: (Height: 36); "
                    + "Style: (FontSize: 12, TextColor: #8193aa, HorizontalAlignment: Center, VerticalAlignment: Center); }");
        } else {
            for (int i = 0; i < warps.size(); i++) {
                PlayerWarpModel warp = warps.get(i);
                cmd.append("#WarpList", ROW_LAYOUT);
                String selector = "#WarpList[" + i + "]";
                String cost = config.getPlayerWarpVisitCost() > 0L && economy != null
                        ? " | " + economy.formatAmount(config.getPlayerWarpVisitCost())
                        : "";
                cmd.set(selector + ".Text", warp.getName() + " - " + warp.getOwnerName() + " | " + warp.getVisits() + " visits" + cost);
                evt.addEventBinding(CustomUIEventBindingType.Activating, selector,
                        EventData.of("Action", "Warp").append("Warp", warp.getName()), false);
            }
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "Close"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {
        if ("Close".equals(data.action)) {
            close();
            return;
        }
        if ("Warp".equals(data.action) && data.warp != null) {
            teleport(ref, store, data.warp);
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void teleport(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String name) {
        PlayerWarpModel warp = playerWarps.findVisibleWarp(name, playerRef.getUuid(),
                CommandPermissionUtil.hasPermission(playerRef, "hyessentialsx.playerwarp.admin"));
        if (warp == null) {
            Messages.sendPrefixedKey(playerRef, "playerwarp.not_found", Map.of());
            return;
        }
        if (config.getPlayerWarpVisitCost() > 0L && economy != null && economy.isEnabled()
                && !CommandPermissionUtil.hasPermission(playerRef, "hyessentialsx.playerwarp.bypasscost")
                && !economy.withdraw(playerRef.getUuid(), config.getPlayerWarpVisitCost())) {
            Messages.sendPrefixedKey(playerRef, "economy.insufficient_funds", Map.of());
            return;
        }
        if (!cooldowns.canUse(playerRef, CooldownKeys.WARP, "/pwarp", "hyessentialsx.playerwarp.bypass")) {
            return;
        }
        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
        if (transform != null && transform.getPosition() != null && store.getExternalData().getWorld() != null) {
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            backManager.recordLocation(playerRef.getUuid(), store.getExternalData().getWorld().getName(),
                    transform.getPosition().x(), transform.getPosition().y(), transform.getPosition().z(),
                    rot == null ? 0F : rot.y(), rot == null ? 0F : rot.x());
        }
        close();
        String err = TeleportationUtil.teleportToLocation(store, ref, warp.getWorldId(), warp.getWorldName(),
                warp.getX(), warp.getY(), warp.getZ(), warp.getYaw(), warp.getPitch());
        if (err != null) {
            Messages.sendPrefixed(playerRef, err);
            return;
        }
        warp.incrementVisits();
        try {
            playerWarps.setWarp(UUID.fromString(warp.getOwnerUuid()), warp);
        } catch (IllegalArgumentException ignored) {
        }
        cooldowns.apply(playerRef, CooldownKeys.WARP);
        Messages.sendPrefixedKey(playerRef, "playerwarp.visited",
                Map.of("warp", warp.getName(), "owner", warp.getOwnerName()));
    }

    public static final class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec
                .builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Warp", Codec.STRING), (d, v) -> d.warp = v, d -> d.warp).add()
                .build();

        private String action;
        private String warp;
    }
}
