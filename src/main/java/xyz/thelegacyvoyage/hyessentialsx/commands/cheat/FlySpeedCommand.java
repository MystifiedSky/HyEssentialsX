package xyz.thelegacyvoyage.hyessentialsx.commands.cheat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class FlySpeedCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.flyspeed";
    private static final String OTHERS_PERMISSION = "hyessentialsx.flyspeed.others";

    private final FlyManager flyManager;
    private final StorageManager storage;
    private final ConfigManager config;

    public FlySpeedCommand(@Nonnull FlyManager flyManager,
                           @Nonnull StorageManager storage,
                           @Nonnull ConfigManager config) {
        super("flyspeed", "Set fly speed multiplier");
        this.flyManager = flyManager;
        this.storage = storage;
        this.config = config;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addUsageVariant(new FlySpeedSelfCommand());
        this.addUsageVariant(new FlySpeedOtherCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/flyspeed");
            return;
        }

        float current = flyManager.getFlySpeedMultiplier(playerRef.getUuid());
        Messages.sendKey(context, "flyspeed.current", Map.of("speed", formatSpeed(current)));
    }

    private void setSpeed(@Nonnull CommandContext context,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull PlayerRef target,
                          @Nonnull Float speed) {
        if (speed == null || !Float.isFinite(speed)) {
            Messages.errKey(context, "flyspeed.invalid", Map.of());
            return;
        }
        float requested = speed;

        float min = (float) config.getFlySpeedMin();
        float max = (float) config.getFlySpeedMax();
        if (requested < min || requested > max) {
            Messages.errKey(context, "flyspeed.range", Map.of(
                    "min", formatSpeed(min),
                    "max", formatSpeed(max)
            ));
            return;
        }

        flyManager.setFlySpeedMultiplier(target.getUuid(), requested);
        var data = storage.getPlayerData(target.getUuid());
        data.setFlySpeedMultiplier(requested);
        storage.savePlayerDataAsync(target.getUuid(), data);

        if (flyManager.isEnabled(target.getUuid())) {
            flyManager.applyState(target, true);
        } else {
            flyManager.applySpeedOnly(target);
        }

        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.okKey(context, "flyspeed.set_self", Map.of("speed", formatSpeed(requested)));
        } else {
            Messages.okKey(context, "flyspeed.set_other", Map.of(
                    "player", target.getUsername(),
                    "speed", formatSpeed(requested)
            ));
            Messages.sendPrefixedKey(target, "flyspeed.changed_by", Map.of(
                    "player", playerRef.getUsername(),
                    "speed", formatSpeed(requested)
            ));
        }
    }

    @Nonnull
    private static String formatSpeed(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private final class FlySpeedSelfCommand extends AbstractPlayerCommand {
        private final RequiredArg<Float> speedArg;

        private FlySpeedSelfCommand() {
            super("Set your fly speed multiplier");
            this.speedArg = withRequiredArg("speed", "Fly speed multiplier", ArgTypes.FLOAT);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/flyspeed");
                return;
            }
            setSpeed(context, playerRef, playerRef, context.get(speedArg));
        }
    }

    private final class FlySpeedOtherCommand extends AbstractPlayerCommand {
        private final RequiredArg<Float> speedArg;
        private final RequiredArg<PlayerRef> targetArg;

        private FlySpeedOtherCommand() {
            super("Set another player's fly speed multiplier");
            this.speedArg = withRequiredArg("speed", "Fly speed multiplier", ArgTypes.FLOAT);
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/flyspeed");
                return;
            }
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
                Messages.noPerm(context, "/flyspeed <speed> <player>");
                return;
            }
            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            setSpeed(context, playerRef, target, context.get(speedArg));
        }
    }
}
