package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

public final class RulesCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.rules";

    private final ConfigManager config;

    public RulesCommand(@Nonnull ConfigManager config) {
        super("rules", "Displays server rules");
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
            Messages.noPerm(context, "/rules");
            return;
        }
        if (!config.isRulesEnabled()) {
            Messages.err(context, "Rules are disabled.");
            return;
        }

        List<String> rules = config.getRules();
        if (rules.isEmpty()) {
            Messages.send(context, "&7No rules set.");
            return;
        }

        Messages.send(context, "&aRules:");
        for (String rule : rules) {
            Messages.send(context, rule);
        }
    }
}



