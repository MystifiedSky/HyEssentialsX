package xyz.thelegacyvoyage.hyessentialsx.commands.custom;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.models.CustomCommandDefinition;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class CustomTextCommand extends CommandBase {

    private final String permission;
    private final String message;

    public CustomTextCommand(@Nonnull CustomCommandDefinition definition) {
        super(definition.getName(), "Custom command");
        this.permission = definition.getPermission();
        this.message = definition.getMessage();
        this.setPermissionGroup(null);
        CommandPermissionUtil.apply(this, permission);
        if (!definition.getAliases().isEmpty()) {
            this.addAliases(definition.getAliases().toArray(new String[0]));
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
        Messages.send(context, message);
    }
}
