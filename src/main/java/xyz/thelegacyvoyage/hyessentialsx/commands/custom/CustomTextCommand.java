package xyz.thelegacyvoyage.hyessentialsx.commands.custom;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CustomCommandManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class CustomTextCommand extends CommandBase {

    private final CustomCommandManager manager;
    private final String permission;
    private final String commandName;

    public CustomTextCommand(@Nonnull CustomCommandManager manager, @Nonnull String name, @Nonnull String permission, @Nonnull java.util.List<String> aliases) {
        super(name, "Custom command");
        this.manager = manager;
        this.commandName = name.toLowerCase();
        this.permission = permission;
        this.setPermissionGroup(null);
        CommandPermissionUtil.apply(this, permission);
        if (!aliases.isEmpty()) {
            this.addAliases(aliases.toArray(new String[0]));
        }
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(permission)) {
            Messages.noPerm(context, "/" + this.getName());
            return;
        }
        var definition = manager.getCommandOrNull(commandName);
        if (definition == null || definition.getMessage() == null || definition.getMessage().isBlank()) {
            Messages.send(context, "&cThis custom command is no longer available.");
            return;
        }
        Messages.send(context, definition.getMessage());
    }
}
