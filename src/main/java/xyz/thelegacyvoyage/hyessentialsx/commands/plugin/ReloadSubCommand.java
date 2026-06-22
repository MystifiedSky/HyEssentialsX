package xyz.thelegacyvoyage.hyessentialsx.commands.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin;

import javax.annotation.Nonnull;

/**
 * /hyessentialsx reload - Reload plugin configuration
 */
public class ReloadSubCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.reload";

    public ReloadSubCommand() {
        super("reload", "Reload plugin configuration");
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
            xyz.thelegacyvoyage.hyessentialsx.util.Messages.noPerm(context, "/hyessentialsx reload");
            return;
        }
        HyEssentialsXPlugin plugin = HyEssentialsXPlugin.getInstance();

        if (plugin == null) {
            context.sendMessage(Message.raw("Error: Plugin not loaded"));
            return;
        }

        context.sendMessage(Message.raw("Reloading HyEssentialsX..."));

        plugin.reloadPlugin();

        context.sendMessage(Message.raw("HyEssentialsX reloaded successfully!"));
        context.sendMessage(Message.raw("Note: adding/removing custom commands or changing storage type requires a server restart."));
    }
}

