package xyz.thelegacyvoyage.hyessentialsx.commands.plugin;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import xyz.thelegacyvoyage.hyessentialsx.commands.importer.ImportHomesCommand;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.LanguageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.AdminCommandCenterContext;

import javax.annotation.Nonnull;
import java.nio.file.Path;

/**
 * Main command for HyEssentialsX plugin.
 *
 * Usage:
 * - /hyessentialsx help - Show available commands
 * - /hyessentialsx info - Show plugin information
 * - /hyessentialsx reload - Reload plugin configuration
 * - /hyessentialsx ui - Open the plugin dashboard
 */
public class HyEssentialsXPluginCommand extends AbstractCommandCollection {

    public HyEssentialsXPluginCommand(@Nonnull StorageManager storage,
                                      @Nonnull SpawnManager spawnManager,
                                      @Nonnull Path dataFolder,
                                      @Nonnull LanguageManager languageManager,
                                      @Nonnull AdminCommandCenterContext dashboardContext) {
        super("hyessentialsx", "HyEssentialsX plugin commands");
        this.addAliases(new String[]{"essentials", "ess"});

        // Add subcommands
        this.addSubCommand(new HelpSubCommand());
        this.addSubCommand(new InfoSubCommand());
        this.addSubCommand(new ReloadSubCommand());
        this.addSubCommand(new UISubCommand(dashboardContext));
        this.addSubCommand(new ImportHomesCommand(storage, dataFolder));
        this.addSubCommand(new MigrateSubCommand(storage, spawnManager, dataFolder));
        this.addSubCommand(new LanguageSubCommand(languageManager));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false; // No permission required for base command
    }
}

