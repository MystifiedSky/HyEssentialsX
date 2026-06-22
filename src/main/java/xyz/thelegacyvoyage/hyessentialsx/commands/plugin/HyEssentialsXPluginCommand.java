package xyz.thelegacyvoyage.hyessentialsx.commands.plugin;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Main command for HyEssentialsX plugin.
 *
 * Usage:
 * - /essentials help - Show available commands
 * - /essentials info - Show plugin information
 * - /essentials reload - Reload plugin configuration
 * - /essentials ui - Open the plugin dashboard
 */
public class HyEssentialsXPluginCommand extends AbstractCommandCollection {

    public HyEssentialsXPluginCommand() {
        super("essentials", "HyEssentialsX plugin commands");

        // Add subcommands
        this.addSubCommand(new HelpSubCommand());
        this.addSubCommand(new InfoSubCommand());
        this.addSubCommand(new ReloadSubCommand());
        this.addSubCommand(new UISubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false; // No permission required for base command
    }
}