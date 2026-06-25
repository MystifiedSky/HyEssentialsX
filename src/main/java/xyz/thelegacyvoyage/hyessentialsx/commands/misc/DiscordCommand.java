package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;

import javax.annotation.Nonnull;
import java.util.Map;

public final class DiscordCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.discord";
    private static final String BYPASS_PERMISSION = "hyessentialsx.discord.bypass";

    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;

    public DiscordCommand(@Nonnull ConfigManager config,
                          @Nonnull CommandCooldownManager cooldowns) {
        super("discord", "Shows server Discord information");
        this.config = config;
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/discord");
            return;
        }
        if (!config.isDiscordEnabled()) {
            Messages.errKey(context, "discord.disabled", Map.of());
            return;
        }

        PlayerRef player = CommandSenderUtil.resolvePlayer(context);
        if (player != null) {
            if (!cooldowns.canUse(context, player, CooldownKeys.DISCORD, "/discord", BYPASS_PERMISSION)) {
                return;
            }
            if (!cooldowns.apply(player, CooldownKeys.DISCORD)) {
                return;
            }
        }
        for (String line : config.getDiscordMessages()) {
            String text = line.replace("{discord}", config.getDiscordInviteUrl());
            context.sendMessage(PlaceholderApiUtil.apply(player, text));
        }
    }
}
