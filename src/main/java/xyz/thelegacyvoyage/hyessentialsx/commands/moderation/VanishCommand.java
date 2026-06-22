package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.MapVisibilityUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

public final class VanishCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.vanish";
    private static final String OTHERS_PERMISSION = "hyessentialsx.vanish.others";

    private final VanishManager vanishManager;

    public VanishCommand(@Nonnull VanishManager vanishManager) {
        super("vanish", "Toggle vanish");
        this.vanishManager = vanishManager;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"v"});
        this.setAllowsExtraArguments(true);
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

        List<String> args = CommandInputUtil.getArgs(context);
        PlayerRef self = CommandSenderUtil.resolvePlayer(context);
        PlayerRef target;
        if (args.isEmpty()) {
            if (self == null) {
                Messages.errKey(context, "error.player_only", java.util.Map.of());
                return;
            }
            target = self;
        } else {
            String targetName = args.get(0);
            target = findOnlinePlayer(targetName);
        }
        if (target == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        boolean isSelf = self != null && self.getUuid().equals(target.getUuid());
        if (!isSelf && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHERS_PERMISSION)) {
            Messages.noPerm(context, "/vanish");
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

    private static PlayerRef findOnlinePlayer(@Nonnull String name) {
        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (ref == null) continue;
            String username = ref.getUsername();
            if (username != null && username.equalsIgnoreCase(name)) {
                return ref;
            }
        }
        return null;
    }
}

