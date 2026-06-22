package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.Map;

public final class BackCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.back";
    private static final String BYPASS_PERMISSION = "hyessentialsx.back.bypass";
    private static final String OTHERS_PERMISSION = "hyessentialsx.back.other";

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
        this.setAllowsExtraArguments(true);
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
        java.util.List<String> args = CommandInputUtil.getArgs(context);
        PlayerRef target = playerRef;
        if (!args.isEmpty()) {
            if (!context.sender().hasPermission(OTHERS_PERMISSION)) {
                Messages.noPerm(context, "/back <player>");
                return;
            }
            String targetName = args.get(0);
            if (targetName == null || targetName.isBlank()) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            target = Universe.get().getPlayerByUsername(targetName, com.hypixel.hytale.server.core.NameMatching.EXACT_IGNORE_CASE);
            if (target == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
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
                    transform.getPosition().clone(),
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




