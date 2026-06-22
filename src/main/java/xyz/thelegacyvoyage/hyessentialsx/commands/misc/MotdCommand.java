package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class MotdCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.motd";

    private final ConfigManager config;

    public MotdCommand(@Nonnull ConfigManager config) {
        super("motd", "Shows Message of the Day");
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/motd");
            return;
        }
        if (!config.isMotdEnabled()) {
            Messages.err(context, "MOTD is disabled.");
            return;
        }

        for (String line : config.getMotdMessages()) {
            Messages.send(context, line);
        }
    }
}



