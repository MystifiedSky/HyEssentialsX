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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WarpsUI extends InteractiveCustomUIPage<WarpsUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/WarpPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/WarpRow.ui";
    private static final String PERMISSION_NODE = "hyessentialsx.warp";
    private static final String PER_WARP_PERMISSION_PREFIX = PERMISSION_NODE + ".";
    private static final String BYPASS_PERMISSION = "hyessentialsx.warp.bypass";

    private final PlayerRef playerRef;
    private final WarpManager warpManager;
    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final BackManager backManager;

    public WarpsUI(@Nonnull PlayerRef playerRef,
                   @Nonnull WarpManager warpManager,
                   @Nonnull TPManager tpManager,
                   @Nonnull ConfigManager config,
                   @Nonnull CommandCooldownManager cooldowns,
                   @Nonnull BackManager backManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.warpManager = warpManager;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.backManager = backManager;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        List<String> warps = filterAccessibleWarps(warpManager.listWarps());
        cmd.set("#WarpCount.Text", warps.size() + " Warps");

        buildWarpsList(cmd, evt, warps);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "Close"),
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
        if (data.action.equals("Close")) {
            close();
            return;
        }

        if (data.action.equals("Warp")) {
            teleportToWarp(ref, store, data.warp);
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
                    EventData.of("Action", "Warp").append("Warp", name),
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
        String displayWarpName = (warp.getName() == null || warp.getName().isBlank()) ? name : warp.getName();
        if (!hasWarpPermission(displayWarpName)) {
            Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of("command", "/warp " + displayWarpName));
            return;
        }

        close();

        int warmupSeconds = config.getWarpWarmupSeconds();
        if (cooldowns.hasWarmupBypass(playerRef, CooldownKeys.WARP, BYPASS_PERMISSION)) {
            warmupSeconds = 0;
        }
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
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.y() : 0f;
            float startPitch = (rot != null) ? rot.x() : 0f;
            org.joml.Vector3d startPos = new org.joml.Vector3d(transform.getPosition());
            String startWorldName = resolveWorldName(store);
            tpManager.queue(
                    playerRef.getUuid(),
                    startPos,
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                ref,
                                warp.getWorldName(),
                                warp.x(), warp.y(), warp.z(),
                                warp.yaw(), warp.pitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(playerRef, err);
                            return;
                        }
                        if (startWorldName != null) {
                            backManager.recordLocation(
                                    playerRef.getUuid(),
                                    startWorldName,
                                    startPos.x(), startPos.y(), startPos.z(),
                                    startYaw, startPitch
                            );
                        }
                        cooldowns.apply(playerRef, CooldownKeys.WARP);
                        Messages.sendPrefixedKey(playerRef, "teleport.success.warp", Map.of("warp", displayWarpName));
                    }
            );
            Messages.sendPrefixedKey(playerRef, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
        if (transform != null && transform.getPosition() != null) {
            String worldName = resolveWorldName(store);
            if (worldName != null) {
                com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
                float startYaw = (rot != null) ? rot.y() : 0f;
                float startPitch = (rot != null) ? rot.x() : 0f;
                backManager.recordLocation(
                        playerRef.getUuid(),
                        worldName,
                        transform.getPosition().x(),
                        transform.getPosition().y(),
                        transform.getPosition().z(),
                        startYaw, startPitch
                );
            }
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                warp.getWorldName(),
                warp.x(), warp.y(), warp.z(),
                warp.yaw(), warp.pitch()
        );
        if (err != null) {
            Messages.sendPrefixed(playerRef, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.WARP);
        Messages.sendPrefixedKey(playerRef, "teleport.success.warp", Map.of("warp", displayWarpName));
    }

    @Nullable
    private String resolveWorldName(@Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        return world != null ? world.getName() : null;
    }

    @Nonnull
    private List<String> filterAccessibleWarps(@Nonnull List<String> warps) {
        if (CommandPermissionUtil.hasPermission(playerRef, PERMISSION_NODE)) {
            return warps;
        }
        List<String> visible = new ArrayList<>();
        for (String warpName : warps) {
            if (hasWarpPermission(warpName)) {
                visible.add(warpName);
            }
        }
        return visible;
    }

    private boolean hasWarpPermission(@Nonnull String warpName) {
        if (CommandPermissionUtil.hasPermission(playerRef, PERMISSION_NODE)) {
            return true;
        }
        String normalized = normalizePermissionSegment(warpName);
        if (normalized.isEmpty()) {
            return false;
        }
        return CommandPermissionUtil.hasPermission(playerRef, PER_WARP_PERMISSION_PREFIX + normalized);
    }

    @Nonnull
    private String normalizePermissionSegment(@Nonnull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
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

