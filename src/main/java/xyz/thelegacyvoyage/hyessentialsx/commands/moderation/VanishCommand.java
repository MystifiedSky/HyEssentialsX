package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.MapVisibilityUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class VanishCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.vanish";
    private static final String OTHERS_PERMISSION = "hyessentialsx.vanish.others";
    private static final String BYPASS_PERMISSION = "hyessentialsx.vanish.bypass";

    private final VanishManager vanishManager;
    private final CommandCooldownManager cooldowns;

    public VanishCommand(@Nonnull VanishManager vanishManager,
                         @Nonnull CommandCooldownManager cooldowns) {
        super("vanish", "Toggle vanish");
        this.vanishManager = vanishManager;
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"v"});
        this.addUsageVariant(new VanishOtherCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/vanish");
            return;
        }

        PlayerRef self = CommandSenderUtil.resolvePlayer(context);
        if (self == null) {
            Messages.errKey(context, "error.player_only", java.util.Map.of());
            return;
        }
        if (!cooldowns.canUse(context, self, CooldownKeys.VANISH, "/vanish", BYPASS_PERMISSION)) {
            return;
        }
        toggleVanish(context, self, self);
    }

    private void toggleVanish(@Nonnull CommandContext context, PlayerRef self, @Nonnull PlayerRef target) {
        boolean isSelf = self != null && self.getUuid().equals(target.getUuid());
        if (!isSelf && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
            Messages.noPerm(context, "/vanish");
            return;
        }
        if (self != null && !cooldowns.apply(self, CooldownKeys.VANISH)) {
            return;
        }

        boolean enabled = vanishManager.toggle(target.getUuid());
        updateVisibility(target, enabled);
        MapVisibilityUtil.refreshAll(vanishManager);

        if (isSelf) {
            Messages.okKey(context, enabled ? "vanish.enabled" : "vanish.disabled", java.util.Map.of());
        } else {
            Messages.okKey(context,
                    enabled ? "vanish.enabled_for" : "vanish.disabled_for",
                    java.util.Map.of("player", target.getUsername()));
            Messages.sendPrefixedKey(target,
                    enabled ? "vanish.enabled_by" : "vanish.disabled_by",
                    java.util.Map.of("player", resolveSenderName(context)));
        }
    }

    private void updateVisibility(@Nonnull PlayerRef target, boolean enabled) {
        for (PlayerRef viewer : Universe.get().getPlayers()) {
            if (viewer == null || viewer.getUuid().equals(target.getUuid())) continue;
            HiddenPlayersManager manager = viewer.getHiddenPlayersManager();
            if (manager == null) continue;
            if (enabled) {
                manager.hidePlayer(target.getUuid());
            } else {
                manager.showPlayer(target.getUuid());
            }
        }
    }

    @Nonnull
    private static String resolveSenderName(@Nonnull CommandContext context) {
        Object sender = context.sender();
        if (sender instanceof PlayerRef player) return player.getUsername();
        return "Console";
    }

    private final class VanishOtherCommand extends CommandBase {
        private final RequiredArg<PlayerRef> targetArg;

        private VanishOtherCommand() {
            super("Toggle vanish for another player");
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/vanish");
                return;
            }
            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", java.util.Map.of());
                return;
            }
            PlayerRef self = CommandSenderUtil.resolvePlayer(context);
            if (self != null && !cooldowns.canUse(context, self, CooldownKeys.VANISH, "/vanish", BYPASS_PERMISSION)) {
                return;
            }
            toggleVanish(context, self, target);
        }
    }

}

