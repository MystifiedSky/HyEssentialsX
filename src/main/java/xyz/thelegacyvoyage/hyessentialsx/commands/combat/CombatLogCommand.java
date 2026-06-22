package xyz.thelegacyvoyage.hyessentialsx.commands.combat;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin;
import xyz.thelegacyvoyage.hyessentialsx.managers.CombatLogManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PluginInfoUtil;

import javax.annotation.Nonnull;
import java.util.List;

public final class CombatLogCommand extends CommandBase {

    private static final String RELOAD_PERMISSION = "hyessentialsx.combatlog.reload";
    private final CombatLogManager combatManager;
    private final ConfigManager config;

    public CombatLogCommand(@Nonnull CombatLogManager combatManager, @Nonnull ConfigManager config) {
        super("combatlog", "Combat log settings");
        this.combatManager = combatManager;
        this.config = config;
        this.setPermissionGroups();
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<String> args = CommandInputUtil.getArgs(context);
        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("reload")) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), RELOAD_PERMISSION)) {
                Messages.noPerm(context, "/combatlog reload");
                return;
            }
            HyEssentialsXPlugin plugin = HyEssentialsXPlugin.getInstance();
            if (plugin == null) {
                context.sendMessage(combatManager.buildPrefixedMessage(null,
                        config.getCombatLogReloadFailedMessage(),
                        java.util.Map.of("error", "plugin not loaded")));
                return;
            }
            try {
                plugin.reloadPlugin();
                context.sendMessage(combatManager.buildPrefixedMessage(null,
                        config.getCombatLogReloadSuccessMessage(), java.util.Map.of()));
            } catch (Exception ex) {
                context.sendMessage(combatManager.buildPrefixedMessage(null,
                        config.getCombatLogReloadFailedMessage(),
                        java.util.Map.of("error", ex.getMessage())));
            }
            return;
        }

        String version = PluginInfoUtil.getVersion();
        context.sendMessage(combatManager.buildPrefixedMessage(null,
                config.getCombatLogCommandInfoMessage(), java.util.Map.of("version", version)));
        context.sendMessage(Messages.m(Messages.tr(null, "combatlog.command_hint", java.util.Map.of())));
        if (!combatManager.isEnabled()) {
            context.sendMessage(Messages.m(Messages.tr(null, "combatlog.disabled", java.util.Map.of())));
        }
    }
}

