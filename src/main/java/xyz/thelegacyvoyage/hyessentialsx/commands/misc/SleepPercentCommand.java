package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class SleepPercentCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.sleeppercent";

    private final ConfigManager config;

    public SleepPercentCommand(@Nonnull ConfigManager config) {
        super("sleeppercent", "Sets or views the sleep percentage");
        this.config = config;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"sp"});
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/sleeppercent");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            Messages.okKey(context, "sleeppercent.current", Map.of(
                    "percent", String.valueOf(config.getSleepPercentage())
            ));
            return;
        }

        String raw = args.get(0);
        Integer value = parsePercent(raw);
        if (value == null) {
            Messages.errKey(context, "sleeppercent.usage", Map.of());
            return;
        }

        config.setSleepPercentage(value);
        Messages.okKey(context, "sleeppercent.updated", Map.of(
                "percent", String.valueOf(config.getSleepPercentage())
        ));
    }

    private static Integer parsePercent(@Nonnull String raw) {
        String trimmed = raw.trim();
        if (!trimmed.matches("\\d+")) return null;
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 0 || value > 100) return null;
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

