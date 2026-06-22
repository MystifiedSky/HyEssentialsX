package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class ListCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.list";

    public ListCommand() {
        super("list", "Shows all online players");
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"online", "playerlist", "plist", "who"});
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/list");
            return;
        }

        List<String> names = new ArrayList<>();
        for (PlayerRef ref : Universe.get().getPlayers()) {
            names.add(ref.getUsername());
        }

        Messages.send(context, "&aOnline (&f" + names.size() + "&a): &f" + String.join(", ", names));
    }
}



