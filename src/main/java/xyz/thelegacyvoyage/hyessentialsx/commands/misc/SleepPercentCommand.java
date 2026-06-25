package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class SleepPercentCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.sleeppercent";

    private final ConfigManager config;

    public SleepPercentCommand(@Nonnull ConfigManager config) {
        super("sleeppercent", "Sets or views the sleep percentage");
        this.config = config;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addUsageVariant(new SetSleepPercentCommand());
        this.addAliases(new String[]{"sp"});
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/sleeppercent");
            return;
        }

        Messages.okKey(context, "sleeppercent.current", Map.of(
                "percent", String.valueOf(config.getSleepPercentage())
        ));
    }

    private void setPercent(@Nonnull CommandContext context, Integer value) {
        if (value == null) {
            Messages.errKey(context, "sleeppercent.usage", Map.of());
            return;
        }
        if (value < 0 || value > 100) {
            Messages.errKey(context, "sleeppercent.usage", Map.of());
            return;
        }

        config.setSleepPercentage(value);
        Messages.okKey(context, "sleeppercent.updated", Map.of(
                "percent", String.valueOf(config.getSleepPercentage())
        ));
    }

    private final class SetSleepPercentCommand extends CommandBase {
        private final RequiredArg<Integer> percentArg;

        private SetSleepPercentCommand() {
            super("Set the sleep percentage");
            this.percentArg = withRequiredArg("percent", "Sleep percentage", ArgTypes.INTEGER);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/sleeppercent");
                return;
            }
            setPercent(context, context.get(percentArg));
        }
    }

}

