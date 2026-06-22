package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.Map;

public final class BackCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.back";
    private static final String BYPASS_PERMISSION = "hyessentialsx.back.bypass";

    private final BackManager backManager;
    private final TPManager tpManager;
    private final CommandCooldownManager cooldowns;
    private final ConfigManager config;

    public BackCommand(@Nonnull BackManager backManager,
                       @Nonnull TPManager tpManager,
                       @Nonnull ConfigManager config,
                       @Nonnull CommandCooldownManager cooldowns) {
        super("back", "Return to your last teleport or death location");
        this.backManager = backManager;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.setPermissionGroup(null);
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
            Messages.noPerm(context, "/back");
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.BACK, "/back", BYPASS_PERMISSION)) {
            return;
        }

        BackManager.BackPoint back = backManager.peek(playerRef.getUuid());
        if (back == null) {
            Messages.errKey(context, "back.none", Map.of());
            return;
        }

        int warmupSeconds = config.getBackWarmupSeconds();
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
                                back.getWorldName(),
                                back.getX(), back.getY(), back.getZ(),
                                back.getYaw(), back.getPitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(playerRef, err);
                            return;
                        }
                        backManager.consume(playerRef.getUuid());
                        cooldowns.apply(playerRef, CooldownKeys.BACK);
                        Messages.sendPrefixedKey(playerRef, "teleport.success.back", Map.of());
                    }
            );
            Messages.sendPrefixedKey(playerRef, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        tpManager.cancel(playerRef.getUuid(), null);
        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                back.getWorldName(),
                back.getX(), back.getY(), back.getZ(),
                back.getYaw(), back.getPitch()
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        backManager.consume(playerRef.getUuid());
        cooldowns.apply(playerRef, CooldownKeys.BACK);
        Messages.okKey(context, "teleport.success.back", Map.of());
    }
}



