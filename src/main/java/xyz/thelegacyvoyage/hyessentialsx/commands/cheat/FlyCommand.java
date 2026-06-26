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
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class FlyCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.fly";
    private static final String OTHERS_PERMISSION = "hyessentialsx.fly.others";
    private static final String BYPASS_PERMISSION = "hyessentialsx.fly.bypass";
    private static final String TIMED_PERMISSION = "hyessentialsx.fly.time";

    private final FlyManager flyManager;
    private final StorageManager storage;
    private final CommandCooldownManager cooldowns;
    private final EconomyManager economy;
    private final ConfigManager config;

    public FlyCommand(@Nonnull FlyManager flyManager,
                      @Nonnull StorageManager storage,
                      @Nonnull CommandCooldownManager cooldowns,
                      @Nonnull EconomyManager economy,
                      @Nonnull ConfigManager config) {
        super("fly", "Toggle flight");
        this.flyManager = flyManager;
        this.storage = storage;
        this.cooldowns = cooldowns;
        this.economy = economy;
        this.config = config;
        this.setPermissionGroups();
        this.addSubCommand(new FlyBuyCommand());
        this.addSubCommand(new FlyTimeCommand());
        this.addUsageVariant(new FlyOtherCommand());
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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/fly");
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.FLY, "/fly", BYPASS_PERMISSION, world)) {
            return;
        }

        toggleFly(context, playerRef, playerRef);
    }

    private void toggleFly(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull PlayerRef target) {
        if (!playerRef.getUuid().equals(target.getUuid())
                && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
            Messages.noPerm(context, "/fly");
            return;
        }

        if (!playerRef.getUuid().equals(target.getUuid())
                && playerRef.getWorldUuid() != null
                && target.getWorldUuid() != null
                && !playerRef.getWorldUuid().equals(target.getWorldUuid())) {
            Messages.errKey(context, "error.target_world", java.util.Map.of());
            return;
        }
        if (!cooldowns.apply(playerRef, CooldownKeys.FLY)) {
            return;
        }

        boolean enabled = flyManager.toggle(target.getUuid());
        if (!flyManager.applyState(target, enabled)) {
            flyManager.queueApply(target.getUuid(), enabled);
        }
        var data = storage.getPlayerData(target.getUuid());
        data.setFlyEnabled(enabled);
        if (!enabled) {
            data.setFlyExpiresAt(0L);
        }
        storage.savePlayerDataAsync(target.getUuid(), data);

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (isSelf) {
            Messages.okKey(context, enabled ? "fly.enabled" : "fly.disabled", java.util.Map.of());
        } else {
            Messages.okKey(context,
                    enabled ? "fly.enabled_for" : "fly.disabled_for",
                    java.util.Map.of("player", target.getUsername()));
            Messages.sendPrefixedKey(target,
                    enabled ? "fly.enabled_by" : "fly.disabled_by",
                    java.util.Map.of("player", playerRef.getUsername()));
        }
    }

    private void buyTimedFly(@Nonnull CommandContext context,
                             @Nonnull PlayerRef playerRef,
                             int minutes) {
        if (!config.isTimedFlightEnabled()) {
            Messages.errKey(context, "fly.timed_disabled", java.util.Map.of());
            return;
        }
        if (config.isTimedFlightRequirePermission()
                && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), TIMED_PERMISSION)) {
            Messages.noPerm(context, "/fly time");
            return;
        }
        int safeMinutes = Math.max(1, minutes);
        long price = Math.max(0L, config.getFlightCostPerMinute()) * safeMinutes;
        if (price > 0L) {
            if (!economy.isEnabled()) {
                Messages.errKey(context, "economy.disabled", java.util.Map.of());
                return;
            }
            if (!economy.withdraw(playerRef.getUuid(), price)) {
                Messages.errKey(context, "command.price.insufficient", java.util.Map.of(
                        "command", "/fly time",
                        "price", economy.formatAmount(price),
                        "balance", economy.formatAmount(economy.getBalance(playerRef.getUuid()))
                ));
                return;
            }
        }
        flyManager.grantTimed(playerRef, safeMinutes);
        Messages.okKey(context, "fly.timed_enabled", java.util.Map.of(
                "time", xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil.formatDurationSeconds((long) safeMinutes * 60L),
                "price", economy.formatAmount(price)
        ));
    }

    private final class FlyBuyCommand extends AbstractPlayerCommand {
        private FlyBuyCommand() {
            super("buy", "Buy the configured timed flight package");
            this.setPermissionGroups();
            if (config.isTimedFlightRequirePermission()) {
                xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, TIMED_PERMISSION);
            }
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
            buyTimedFly(context, playerRef, config.getFlightDefaultMinutes());
        }
    }

    private final class FlyTimeCommand extends AbstractPlayerCommand {
        private final RequiredArg<Integer> minutesArg;

        private FlyTimeCommand() {
            super("time", "Buy timed flight minutes");
            this.setPermissionGroups();
            if (config.isTimedFlightRequirePermission()) {
                xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, TIMED_PERMISSION);
            }
            this.minutesArg = withRequiredArg("minutes", "Minutes of flight", ArgTypes.INTEGER);
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
            buyTimedFly(context, playerRef, context.get(minutesArg));
        }
    }

    private final class FlyOtherCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> targetArg;

        private FlyOtherCommand() {
            super("Toggle another player's flight");
            this.setPermissionGroups();
            xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, OTHERS_PERMISSION);
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
                Messages.noPerm(context, "/fly");
                return;
            }
            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", java.util.Map.of());
                return;
            }
            if (!cooldowns.canUse(context, playerRef, CooldownKeys.FLY, "/fly", BYPASS_PERMISSION, world)) {
                return;
            }
            toggleFly(context, playerRef, target);
        }
    }
}




