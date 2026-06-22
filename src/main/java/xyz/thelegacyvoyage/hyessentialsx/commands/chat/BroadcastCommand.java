package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

public final class BroadcastCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.broadcast";

    private final ConfigManager config;
    private final RequiredArg<List<String>> msgArg;

    public BroadcastCommand(@Nonnull ConfigManager config) {
        super("broadcast", "Broadcasts message");
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"bc", "alert", "bcast"});
        this.msgArg = withListRequiredArg("message", "Message", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/broadcast");
            return;
        }
        if (!config.isBroadcastEnabled()) {
            Messages.err(context, "Broadcasts are disabled.");
            return;
        }

        List<String> parts = context.get(msgArg);
        String message = String.join(" ", parts);
        if (message.isBlank()) {
            Messages.err(context, "Message required.");
            return;
        }

        Message msg = Messages.m("&c[Broadcast] &f" + message);
        Universe.get().sendMessage(msg);
    }
}



