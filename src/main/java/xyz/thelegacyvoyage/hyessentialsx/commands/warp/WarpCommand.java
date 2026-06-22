package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.WarpsUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class WarpCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.warp";
    private static final String BYPASS_PERMISSION = "hyessentialsx.warp.bypass";

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
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/warp");
            return;
        }
        if (!config.isWarpsEnabled()) {
            Messages.errKey(context, "warp.disabled", Map.of());
            return;
        }
        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            List<String> warps = warpManager.listWarps();
            if (warps.isEmpty()) {
                Messages.errKey(context, "warp.not_found", Map.of());
                return;
            }
            if (config.isWarpsGuiEnabled()) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    Messages.errKey(context, "warp.ui_failed", Map.of());
                    return;
                }
                WarpsUI page = new WarpsUI(playerRef, warpManager, tpManager, config, cooldowns, backManager);
                page.open(player, ref, store);
                return;
            }
            Messages.sendKey(context, "warp.list", Map.of("warps", String.join(", ", warps)));
            return;
        }
        String name = args.get(0);
        if (name == null || name.trim().isEmpty()) {
            Messages.errKey(context, "warp.not_found", Map.of());
            return;
        }

        if (!cooldowns.canUse(context, playerRef, CooldownKeys.WARP, "/warp", BYPASS_PERMISSION)) {
            return;
        }

        name = name.trim();
        final String warpName = name;
        WarpModel warp = warpManager.getWarp(warpName);
        if (warp == null) {
            Messages.errKey(context, "warp.not_found", Map.of());
            return;
        }

        int warmupSeconds = config.getWarpWarmupSeconds();
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(playerRef.getUuid())) {
                Messages.errKey(context, "teleport.pending", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
            if (transform == null || transform.getPosition() == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            com.hypixel.hytale.math.vector.Vector3d startPos = transform.getPosition().clone();
            tpManager.queue(
                    playerRef.getUuid(),
                    startPos,
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
                        backManager.recordLocation(
                                playerRef.getUuid(),
                                world.getName(),
                                startPos.getX(), startPos.getY(), startPos.getZ(),
                                startYaw, startPitch
                        );
                        cooldowns.apply(playerRef, CooldownKeys.WARP);
                        Messages.sendPrefixedKey(playerRef, "teleport.success.warp", Map.of("warp", warpName));
                    }
            );
            Messages.sendPrefixedKey(playerRef, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
        if (transform != null && transform.getPosition() != null) {
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            backManager.recordLocation(
                    playerRef.getUuid(),
                    world.getName(),
                    transform.getPosition().getX(), transform.getPosition().getY(), transform.getPosition().getZ(),
                    startYaw, startPitch
            );
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                warp.getWorldName(),
                warp.getX(), warp.getY(), warp.getZ(),
                warp.getYaw(), warp.getPitch()
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.WARP);
        Messages.okKey(context, "teleport.success.warp", Map.of("warp", warpName));
    }
}




