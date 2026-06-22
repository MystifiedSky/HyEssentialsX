package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
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
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
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
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
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
        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            handleWarpList(context, senderPlayer);
            return;
        }

        String name = args.get(0);
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
        if (args.size() >= 2) {
            String targetName = args.get(1);
            if (targetName == null || targetName.isBlank()) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            target = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
            if (target == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
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

        if (isOther) {
            if (!cooldowns.canUse(target, CooldownKeys.WARP, "/warp", BYPASS_PERMISSION)) {
                Messages.errKey(context, "error.target_cooldown", Map.of());
                return;
            }
        } else {
            if (!cooldowns.canUse(context, target, CooldownKeys.WARP, "/warp", BYPASS_PERMISSION)) {
                return;
            }
        }

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

        int warmupSeconds = config.getWarpWarmupSeconds();
        if (cooldowns.hasWarmupBypass(context, target, CooldownKeys.WARP, BYPASS_PERMISSION)) {
            warmupSeconds = 0;
        }
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
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            com.hypixel.hytale.math.vector.Vector3d startPos = transform.getPosition().clone();
            final PlayerRef finalTarget = target;
            final java.util.UUID finalTargetId = target.getUuid();
            final Ref<EntityStore> finalTargetRef = targetRef;
            final String startWorldName = targetWorld.getName();
            tpManager.queue(
                    finalTargetId,
                    startPos,
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                finalTargetRef,
                                warp.getWorldName(),
                                warp.getX(), warp.getY(), warp.getZ(),
                                warp.getYaw(), warp.getPitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(finalTarget, err);
                            return;
                        }
                        backManager.recordLocation(
                                finalTargetId,
                                startWorldName,
                                startPos.getX(), startPos.getY(), startPos.getZ(),
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
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            backManager.recordLocation(
                    target.getUuid(),
                    targetWorld.getName(),
                    transform.getPosition().getX(), transform.getPosition().getY(), transform.getPosition().getZ(),
                    startYaw, startPitch
            );
        }

        String err = TeleportationUtil.teleportToLocation(
                targetStore,
                targetRef,
                warp.getWorldName(),
                warp.getX(), warp.getY(), warp.getZ(),
                warp.getYaw(), warp.getPitch()
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

    @Nonnull
    private String normalizePermissionSegment(@Nonnull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}




