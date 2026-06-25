package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class ListCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.list";
    private static final String BYPASS_PERMISSION = "hyessentialsx.list.bypass";

    private final CommandCooldownManager cooldowns;

    public ListCommand(@Nonnull CommandCooldownManager cooldowns) {
        super("list", "Shows all online players");
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"online", "playerlist", "plist", "who"});
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/list");
            return;
        }
        PlayerRef actor = CommandSenderUtil.resolvePlayer(context);
        if (actor != null) {
            if (!cooldowns.canUse(context, actor, CooldownKeys.LIST, "/list", BYPASS_PERMISSION)) {
                return;
            }
            if (!cooldowns.apply(actor, CooldownKeys.LIST)) {
                return;
            }
        }

        List<String> names = new ArrayList<>();
        for (PlayerRef ref : Universe.get().getPlayers()) {
            names.add(ref.getUsername());
        }

        Messages.send(context, Messages.tr(null, "list.format", java.util.Map.of(
                "count", String.valueOf(names.size()),
                "players", String.join(", ", names)
        )));
    }
}




