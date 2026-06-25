package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.WarpsUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
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

public final class WarpCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.warp";
    private static final String PER_WARP_PERMISSION_PREFIX = PERMISSION_NODE + ".";
    private static final String BYPASS_PERMISSION = "hyessentialsx.warp.bypass";
    private static final String OTHER_PERMISSION = "hyessentialsx.warp.other";

    private final WarpManager warpManager;
    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final BackManager backManager;

    public WarpCommand(@Nonnull WarpManager warpManager,
                       @Nonnull TPManager tpManager,
                       @Nonnull ConfigManager config,
                       @Nonnull CommandCooldownManager cooldowns,
                       @Nonnull BackManager backManager) {
        super("warp", "Teleports to a warp");
        this.warpManager = warpManager;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.backManager = backManager;
        this.setPermissionGroups();
        this.addUsageVariant(new WarpToCommand());
        this.addUsageVariant(new WarpOtherCommand());
        this.addSubCommand(new ListSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!config.isWarpsEnabled()) {
            Messages.errKey(context, "warp.disabled", Map.of());
            return;
        }

        PlayerRef senderPlayer = CommandSenderUtil.resolvePlayer(context);
        handleWarpList(context, senderPlayer);
    }

    private void handleWarp(@Nonnull CommandContext context,
                            @Nullable String rawName,
                            @Nullable PlayerRef targetOverride) {
        PlayerRef senderPlayer = CommandSenderUtil.resolvePlayer(context);
        String name = rawName;
        if (name == null || name.trim().isEmpty()) {
            Messages.errKey(context, "warp.not_found", Map.of());
            return;
        }

        name = name.trim();
        final String warpName = name;
        WarpModel warp = warpManager.getWarp(warpName);
        if (warp == null) {
            Messages.errKey(context, "warp.not_found", Map.of());
            return;
        }
        String displayWarpName = (warp.getName() == null || warp.getName().isBlank()) ? warpName : warp.getName();
        if (!hasWarpPermission(context.sender(), displayWarpName)) {
            Messages.noPerm(context, "/warp " + displayWarpName);
            return;
        }

        PlayerRef target = senderPlayer;
        if (targetOverride != null) {
            target = targetOverride;
        }
        if (target == null) {
            Messages.err(context, "&cUsage: /warp <warp> <player>");
            return;
        }

        boolean isOther = senderPlayer == null || !target.getUuid().equals(senderPlayer.getUuid());
        if (isOther
                && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHER_PERMISSION)) {
            Messages.noPerm(context, "/warp <warp> <player>");
            return;
        }

        final String resolvedWarpName = displayWarpName;

        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef == null || !targetRef.isValid()) {
            Messages.errKey(context, "error.target_access", Map.of());
            return;
        }
        Store<EntityStore> targetStore = targetRef.getStore();
        if (targetStore == null) {
            Messages.errKey(context, "error.target_access", Map.of());
            return;
        }
        World targetWorld = resolveTargetWorld(targetStore, target);
        if (targetWorld == null) {
            Messages.errKey(context, "error.world_not_loaded", Map.of());
            return;
        }

        if (isOther) {
            if (!cooldowns.canUse(target, CooldownKeys.WARP, "/warp", BYPASS_PERMISSION, targetWorld)) {
                Messages.errKey(context, "error.target_cooldown", Map.of());
                return;
            }
        } else {
            if (!cooldowns.canUse(context, target, CooldownKeys.WARP, "/warp", BYPASS_PERMISSION, targetWorld)) {
                return;
            }
        }

        int warmupSeconds = cooldowns.getEffectiveWarmupSeconds(context.sender(), target, CooldownKeys.WARP, BYPASS_PERMISSION);
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(target.getUuid())) {
                if (isOther) {
                    Messages.errKey(context, "error.target_pending_teleport", Map.of());
                } else {
                    Messages.errKey(context, "teleport.pending", Map.of());
                }
                return;
            }
            com.hypixel.hytale.math.vector.Transform transform = target.getTransform();
            if (transform == null || transform.getPosition() == null) {
                if (isOther) {
                    Messages.errKey(context, "error.target_position_unavailable", Map.of());
                } else {
                    Messages.errKey(context, "teleport.position_unavailable", Map.of());
                }
                return;
            }
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.y() : 0f;
            float startPitch = (rot != null) ? rot.x() : 0f;
            org.joml.Vector3d startPos = new org.joml.Vector3d(transform.getPosition());
            final PlayerRef finalTarget = target;
            final java.util.UUID finalTargetId = target.getUuid();
            final Ref<EntityStore> finalTargetRef = targetRef;
            final String startWorldName = targetWorld.getName();
            tpManager.queue(
                    finalTargetId,
                    startPos,
                    warmupSeconds,
                    cooldowns.shouldCancelWarmupOnMove(CooldownKeys.WARP),
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                finalTargetRef,
                                warp.getWorldName(),
                                warp.x(), warp.y(), warp.z(),
                                warp.yaw(), warp.pitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(finalTarget, err);
                            return;
                        }
                        backManager.recordLocation(
                                finalTargetId,
                                startWorldName,
                                startPos.x(), startPos.y(), startPos.z(),
                                startYaw, startPitch
                        );
                        cooldowns.apply(finalTarget, CooldownKeys.WARP);
                        Messages.sendPrefixedKey(finalTarget, "teleport.success.warp", Map.of("warp", resolvedWarpName));
                    }
            );
            Messages.sendPrefixedKey(target, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            if (isOther) {
                Messages.ok(context, "Queued " + target.getUsername() + " for warp '" + resolvedWarpName + "'.");
            }
            return;
        }

        com.hypixel.hytale.math.vector.Transform transform = target.getTransform();
        if (transform != null && transform.getPosition() != null) {
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.y() : 0f;
            float startPitch = (rot != null) ? rot.x() : 0f;
            backManager.recordLocation(
                    target.getUuid(),
                    targetWorld.getName(),
                    transform.getPosition().x(), transform.getPosition().y(), transform.getPosition().z(),
                    startYaw, startPitch
            );
        }

        String err = TeleportationUtil.teleportToLocation(
                targetStore,
                targetRef,
                warp.getWorldName(),
                warp.x(), warp.y(), warp.z(),
                warp.yaw(), warp.pitch()
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        cooldowns.apply(target, CooldownKeys.WARP);
        if (isOther) {
            Messages.sendPrefixedKey(target, "teleport.success.warp", Map.of("warp", resolvedWarpName));
            Messages.ok(context, "Teleported " + target.getUsername() + " to warp '" + resolvedWarpName + "'.");
            return;
        }
        Messages.okKey(context, "teleport.success.warp", Map.of("warp", resolvedWarpName));
    }

    private final class WarpToCommand extends CommandBase {
        private final RequiredArg<String> warpArg;

        private WarpToCommand() {
            super("Teleports to a warp");
            this.warpArg = withRequiredArg("warp", "Warp name", ArgTypes.STRING);
            this.warpArg.suggest(WarpCommand.this::suggestWarps);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!config.isWarpsEnabled()) {
                Messages.errKey(context, "warp.disabled", Map.of());
                return;
            }
            handleWarp(context, context.get(warpArg), null);
        }
    }

    private final class WarpOtherCommand extends CommandBase {
        private final RequiredArg<String> warpArg;
        private final RequiredArg<PlayerRef> targetArg;

        private WarpOtherCommand() {
            super("Teleports another player to a warp");
            this.warpArg = withRequiredArg("warp", "Warp name", ArgTypes.STRING);
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.warpArg.suggest(WarpCommand.this::suggestWarps);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!config.isWarpsEnabled()) {
                Messages.errKey(context, "warp.disabled", Map.of());
                return;
            }
            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            handleWarp(context, context.get(warpArg), target);
        }
    }

    private final class ListSubCommand extends CommandBase {
        private ListSubCommand() {
            super("list", "List available warps");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!config.isWarpsEnabled()) {
                Messages.errKey(context, "warp.disabled", Map.of());
                return;
            }
            handleWarpList(context, CommandSenderUtil.resolvePlayer(context));
        }
    }

    private void handleWarpList(@Nonnull CommandContext context, @Nullable PlayerRef senderPlayer) {
        List<String> warps = warpManager.listWarps();
        if (warps.isEmpty()) {
            Messages.errKey(context, "warp.not_found", Map.of());
            return;
        }
        List<String> visibleWarps = filterAccessibleWarps(context.sender(), warps);
        if (visibleWarps.isEmpty()) {
            Messages.noPerm(context, "/warp");
            return;
        }
        if (!config.isWarpsGuiEnabled() || senderPlayer == null) {
            Messages.sendKey(context, "warp.list", Map.of("warps", String.join(", ", visibleWarps)));
            return;
        }
        World senderWorld = resolvePlayerWorld(senderPlayer);
        if (senderWorld == null) {
            Messages.sendKey(context, "warp.list", Map.of("warps", String.join(", ", visibleWarps)));
            return;
        }
        senderWorld.execute(() -> openWarpListUi(context, senderPlayer));
    }

    private void openWarpListUi(@Nonnull CommandContext context, @Nonnull PlayerRef senderPlayer) {
        Ref<EntityStore> senderRef = senderPlayer.getReference();
        Store<EntityStore> senderStore = senderRef != null ? senderRef.getStore() : null;
        if (senderRef == null || !senderRef.isValid() || senderStore == null) {
            Messages.errKey(context, "warp.ui_failed", Map.of());
            return;
        }
        Player player = senderStore.getComponent(senderRef, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "warp.ui_failed", Map.of());
            return;
        }
        WarpsUI page = new WarpsUI(senderPlayer, warpManager, tpManager, config, cooldowns, backManager);
        page.open(player, senderRef, senderStore);
    }

    @Nullable
    private World resolvePlayerWorld(@Nonnull PlayerRef playerRef) {
        try {
            if (playerRef.getWorldUuid() != null) {
                World world = Universe.get().getWorld(playerRef.getWorldUuid());
                if (world != null) {
                    return world;
                }
            }
        } catch (Exception ignored) {
        }
        Ref<EntityStore> ref = playerRef.getReference();
        Store<EntityStore> store = ref != null ? ref.getStore() : null;
        if (store == null) {
            return null;
        }
        try {
            if (store.getExternalData() != null && store.getExternalData().getWorld() != null) {
                return store.getExternalData().getWorld();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private World resolveTargetWorld(@Nonnull Store<EntityStore> targetStore, @Nonnull PlayerRef target) {
        try {
            if (targetStore.getExternalData() != null && targetStore.getExternalData().getWorld() != null) {
                return targetStore.getExternalData().getWorld();
            }
        } catch (Exception ignored) {
        }
        if (target.getWorldUuid() != null) {
            return Universe.get().getWorld(target.getWorldUuid());
        }
        return null;
    }

    @Nonnull
    private List<String> filterAccessibleWarps(@Nullable Object sender, @Nonnull List<String> warps) {
        if (xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(sender, PERMISSION_NODE)) {
            return warps;
        }
        List<String> visible = new ArrayList<>();
        for (String warpName : warps) {
            if (hasWarpPermission(sender, warpName)) {
                visible.add(warpName);
            }
        }
        return visible;
    }

    private boolean hasWarpPermission(@Nullable Object sender, @Nonnull String warpName) {
        if (xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(sender, PERMISSION_NODE)) {
            return true;
        }
        String normalized = normalizePermissionSegment(warpName);
        if (normalized.isEmpty()) {
            return false;
        }
        return xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(
                sender,
                PER_WARP_PERMISSION_PREFIX + normalized
        );
    }

    private void suggestWarps(com.hypixel.hytale.server.core.command.system.CommandSender sender,
                              String input,
                              int offset,
                              com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult result) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        for (String warpName : filterAccessibleWarps(sender, warpManager.listWarps())) {
            if (warpName == null || warpName.isBlank()) continue;
            if (!normalized.isEmpty() && !warpName.toLowerCase(Locale.ROOT).startsWith(normalized)) continue;
            result.suggest(warpName);
        }
    }

    @Nonnull
    private String normalizePermissionSegment(@Nonnull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}




