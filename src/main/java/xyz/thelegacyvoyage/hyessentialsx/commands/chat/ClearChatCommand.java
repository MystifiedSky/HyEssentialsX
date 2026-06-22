package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class ClearChatCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.clearchat";

    public ClearChatCommand() {
        super("clearchat", "Clears the chat");
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"cc"});
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/clearchat");
            return;
        }

        for (PlayerRef player : Universe.get().getPlayers()) {
            for (int i = 0; i < 100; i++) {
                player.sendMessage(Messages.m(""));
            }
        }
    }
}




