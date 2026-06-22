package xyz.thelegacyvoyage.hyessentialsx.commands.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin;
import xyz.thelegacyvoyage.hyessentialsx.util.PluginInfoUtil;

import javax.annotation.Nonnull;

/**
 * /hyessentialsx info - Show plugin information
 */
public class InfoSubCommand extends CommandBase {

    public InfoSubCommand() {
        super("info", "Show plugin information");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        HyEssentialsXPlugin plugin = HyEssentialsXPlugin.getInstance();

        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== HyEssentialsX Info ==="));
        context.sendMessage(Message.raw("Name: HyEssentialsX"));
        context.sendMessage(Message.raw("Version: " + PluginInfoUtil.getVersion()));
        context.sendMessage(Message.raw("Author: MystifiedSky"));
        context.sendMessage(Message.raw("Status: " + (plugin != null ? "Running" : "Not loaded")));
        context.sendMessage(Message.raw("===================="));
    }
}
