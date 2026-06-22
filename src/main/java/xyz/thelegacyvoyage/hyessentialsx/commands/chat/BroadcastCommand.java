package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class BroadcastCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.broadcast";

    private final ConfigManager config;
    public BroadcastCommand(@Nonnull ConfigManager config) {
        super("broadcast", "Broadcasts message");
        this.config = config;
        this.setPermissionGroups();
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"bc", "alert", "bcast"});
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

        List<String> parts = CommandInputUtil.getArgs(context);
        String message = String.join(" ", parts);
        if (message.isBlank()) {
            Messages.errKey(context, "broadcast.message_required", Map.of());
            return;
        }

        Message msg = Messages.m(Messages.tr(null, "broadcast.format", Map.of("message", message)));
        Universe.get().sendMessage(msg);
    }
}




