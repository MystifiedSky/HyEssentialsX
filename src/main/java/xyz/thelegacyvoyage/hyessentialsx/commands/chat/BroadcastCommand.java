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
import java.util.Map;

public final class BroadcastCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.broadcast";

    private final ConfigManager config;
    private final RequiredArg<List<String>> messageArg;

    public BroadcastCommand(@Nonnull ConfigManager config) {
        super("broadcast", "Broadcasts message");
        this.config = config;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"bc", "alert", "bcast"});
        this.messageArg = withListRequiredArg("message", "Message to broadcast", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/broadcast");
            return;
        }
        if (!config.isBroadcastEnabled()) {
            Messages.errKey(context, "broadcast.disabled", Map.of());
            return;
        }

        String message = String.join(" ", context.get(messageArg));
        if (message.isBlank()) {
            Messages.errKey(context, "broadcast.message_required", Map.of());
            return;
        }

        Message msg = Messages.m(Messages.tr(null, "broadcast.format", Map.of("message", message)));
        Universe.get().sendMessage(msg);
    }
}




