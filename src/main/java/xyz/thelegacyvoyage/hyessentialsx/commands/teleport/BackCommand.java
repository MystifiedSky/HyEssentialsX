package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.Map;

public final class BackCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.back";
    private static final String BYPASS_PERMISSION = "hyessentialsx.back.bypass";
    private static final String OTHERS_PERMISSION = "hyessentialsx.back.other";

    private final BackManager backManager;
    private final TPManager tpManager;
    private final CommandCooldownManager cooldowns;
    private final ConfigManager config;
    private final OptionalArg<PlayerRef> targetArg;

    public BackCommand(@Nonnull BackManager backManager,
                       @Nonnull TPManager tpManager,
                       @Nonnull ConfigManager config,
                       @Nonnull CommandCooldownManager cooldowns) {
        super("back", "Return to your last teleport or death location");
        this.backManager = backManager;
        this.tpManager = tpManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.targetArg = withOptionalArg("player", "Target player", ArgTypes.PLAYER_REF);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/back");
            return;
        }
        PlayerRef senderPlayer = CommandSenderUtil.resolvePlayer(context);
        PlayerRef target = senderPlayer;
        if (context.provided(targetArg)) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
                Messages.noPerm(context, "/back <player>");
                return;
            }
            target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
        }
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        if (!cooldowns.canUse(context, target, CooldownKeys.BACK, "/back", BYPASS_PERMISSION)) {
            return;
        }

        BackManager.BackPoint back = backManager.peek(target.getUuid());
        if (back == null) {
            Messages.errKey(context, "back.none", Map.of());
            return;
        }

        World targetWorld = Universe.get().getWorld(target.getWorldUuid());
        if (targetWorld == null) {
            Messages.errKey(context, "error.world_not_loaded", Map.of());
            return;
        }
        Store<EntityStore> targetStore = targetWorld.getEntityStore().getStore();
        Ref<EntityStore> targetRef = targetWorld.getEntityStore().getRefFromUUID(target.getUuid());
        if (targetRef == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        int warmupSeconds = config.getBackWarmupSeconds();
        if (cooldowns.hasWarmupBypass(context, target, CooldownKeys.BACK, BYPASS_PERMISSION)) {
            warmupSeconds = 0;
        }
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(target.getUuid())) {
                Messages.errKey(context, "teleport.pending", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Transform transform = target.getTransform();
            if (transform == null || transform.getPosition() == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }
            final PlayerRef finalTarget = target;
            final UUID finalTargetId = target.getUuid();
            tpManager.queue(
                    finalTargetId,
                    new org.joml.Vector3d(transform.getPosition()),
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                targetRef,
                                back.getWorldName(),
                                back.getX(), back.getY(), back.getZ(),
                                back.getYaw(), back.getPitch()
                        );
                        if (err != null) {
                            Messages.sendPrefixed(finalTarget, err);
                            return;
                        }
                        backManager.consume(finalTargetId);
                        cooldowns.apply(finalTarget, CooldownKeys.BACK);
                        Messages.sendPrefixedKey(finalTarget, "teleport.success.back", Map.of());
                    }
            );
            Messages.sendPrefixedKey(target, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        tpManager.cancel(target.getUuid(), null);
        String err = TeleportationUtil.teleportToLocation(
                targetStore,
                targetRef,
                back.getWorldName(),
                back.getX(), back.getY(), back.getZ(),
                back.getYaw(), back.getPitch()
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        backManager.consume(target.getUuid());
        cooldowns.apply(target, CooldownKeys.BACK);
        Messages.okKey(context, "teleport.success.back", Map.of());
    }
}




