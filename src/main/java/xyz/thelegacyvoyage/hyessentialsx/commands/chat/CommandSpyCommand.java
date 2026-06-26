package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandSpyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CommandSpyCommand extends CommandBase {

    private final CommandSpyManager commandSpy;

    public CommandSpyCommand(@Nonnull CommandSpyManager commandSpy) {
        super("commandspy", "Toggle command spy monitoring");
        this.commandSpy = commandSpy;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, CommandSpyManager.PERMISSION_NODE);
        this.addAliases(new String[]{"cmdspy", "cspy"});
        this.addSubCommand(new ToggleSubCommand());
        this.addSubCommand(new StatusSubCommand());
        this.addSubCommand(new FiltersSubCommand());
        this.addSubCommand(new FilterSubCommand());
        this.addSubCommand(new UnfilterSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        toggle(context);
    }

    private PlayerRef requireStaffPlayer(@Nonnull CommandContext context, @Nonnull String command) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), CommandSpyManager.PERMISSION_NODE)) {
            Messages.noPerm(context, command);
            return null;
        }
        PlayerRef player = CommandSenderUtil.resolvePlayer(context);
        if (player == null) {
            Messages.errKey(context, "error.player_only", Map.of());
            return null;
        }
        return player;
    }

    private void toggle(@Nonnull CommandContext context) {
        PlayerRef player = requireStaffPlayer(context, "/commandspy");
        if (player == null) {
            return;
        }
        boolean enabled = commandSpy.toggle(player.getUuid());
        Messages.okKey(context, enabled ? "commandspy.enabled" : "commandspy.disabled", Map.of());
    }

    private void status(@Nonnull CommandContext context) {
        PlayerRef player = requireStaffPlayer(context, "/commandspy status");
        if (player == null) {
            return;
        }
        Set<String> filters = commandSpy.filters(player.getUuid());
        Messages.sendKey(context, "commandspy.status", Map.of(
                "state", commandSpy.isEnabled(player.getUuid()) ? "enabled" : "disabled",
                "filters", filters.isEmpty() ? "all commands" : String.join(", ", filters)
        ));
    }

    private void addFilter(@Nonnull CommandContext context, @Nonnull String command) {
        PlayerRef player = requireStaffPlayer(context, "/commandspy filter <command>");
        if (player == null) {
            return;
        }
        commandSpy.addFilter(player.getUuid(), command);
        Messages.okKey(context, "commandspy.filter_added", Map.of("command", command));
    }

    private void removeFilter(@Nonnull CommandContext context, @Nonnull String command) {
        PlayerRef player = requireStaffPlayer(context, "/commandspy unfilter <command>");
        if (player == null) {
            return;
        }
        commandSpy.removeFilter(player.getUuid(), command);
        Messages.okKey(context, "commandspy.filter_removed", Map.of("command", command));
    }

    private final class ToggleSubCommand extends CommandBase {
        private ToggleSubCommand() {
            super("toggle", "Toggle command spy monitoring");
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, CommandSpyManager.PERMISSION_NODE);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            toggle(context);
        }
    }

    private final class StatusSubCommand extends CommandBase {
        private StatusSubCommand() {
            super("status", "Show command spy status and filters");
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, CommandSpyManager.PERMISSION_NODE);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            status(context);
        }
    }

    private final class FiltersSubCommand extends CommandBase {
        private FiltersSubCommand() {
            super("filters", "Show command spy filters");
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, CommandSpyManager.PERMISSION_NODE);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            status(context);
        }
    }

    private final class FilterSubCommand extends CommandBase {
        private final RequiredArg<String> commandArg;

        private FilterSubCommand() {
            super("filter", "Only show a command when filters are active");
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, CommandSpyManager.PERMISSION_NODE);
            this.commandArg = withRequiredArg("command", "Command name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            addFilter(context, context.get(commandArg));
        }
    }

    private final class UnfilterSubCommand extends CommandBase {
        private final RequiredArg<String> commandArg;

        private UnfilterSubCommand() {
            super("unfilter", "Remove a command from command spy filters");
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, CommandSpyManager.PERMISSION_NODE);
            this.commandArg = withRequiredArg("command", "Command name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            removeFilter(context, context.get(commandArg));
        }
    }
}
