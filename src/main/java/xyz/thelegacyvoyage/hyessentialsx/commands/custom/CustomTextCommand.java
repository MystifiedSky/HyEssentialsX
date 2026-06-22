package xyz.thelegacyvoyage.hyessentialsx.commands.custom;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.managers.CustomCommandManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;

import javax.annotation.Nonnull;
import java.util.Map;

public final class CustomTextCommand extends CommandBase {

    private final CustomCommandManager manager;
    private final ConfigManager config;
    private final String permission;
    private final String commandName;

    public CustomTextCommand(@Nonnull CustomCommandManager manager,
                             @Nonnull ConfigManager config,
                             @Nonnull String name,
                             @Nonnull String permission,
                             @Nonnull java.util.List<String> aliases) {
        super(name, "Custom command");
        this.manager = manager;
        this.config = config;
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
            Messages.errKey(context, "custom.unavailable", Map.of());
            return;
        }
        var player = CommandSenderUtil.resolvePlayer(context);
        if (player == null) {
            Messages.send(context, definition.getMessage());
            return;
        }
        context.sendMessage(PlaceholderApiUtil.apply(player, definition.getMessage(), config));
    }
}
