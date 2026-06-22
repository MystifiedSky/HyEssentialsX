package xyz.thelegacyvoyage.hyessentialsx.commands.warp;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.Map;

public final class WarpCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.warp";
    private static final String BYPASS_PERMISSION = "hyessentialsx.warp.bypass";

    private final WarpManager warpManager;
    private final TPManager tpManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final RequiredArg<String> nameArg;

    public WarpCommand(@Nonnull WarpManager warpManager,
                       @Nonnull TPManager tpManager,
                       @Nonnull ConfigManager config,
                       @Nonnull CommandCooldownManager cooldowns) {
        super("warp", "Teleports to a warp");
        this.warpManager = warpManager;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
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
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.WARP, "/warp", BYPASS_PERMISSION)) {
            return;
        }

        String name = context.get(nameArg);
        WarpModel warp = warpManager.getWarp(name);
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
            Messages.err(context, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.WARP);
        Messages.okKey(context, "teleport.success.warp", Map.of("warp", name));
    }
}



