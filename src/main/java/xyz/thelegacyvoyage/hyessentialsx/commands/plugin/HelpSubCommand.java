package xyz.thelegacyvoyage.hyessentialsx.commands.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * /hyessentialsx help - Show available commands
 */
public class HelpSubCommand extends CommandBase {

    public HelpSubCommand() {
        super("help", "Show available commands");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== HyEssentialsX Commands ==="));
        context.sendMessage(Message.raw("/hyessentialsx help - Show this help message"));
        context.sendMessage(Message.raw("/hyessentialsx info - Show plugin information"));
        context.sendMessage(Message.raw("/hyessentialsx reload - Reload configuration"));
        context.sendMessage(Message.raw("/hyessentialsx ui - Open the dashboard UI"));
        context.sendMessage(Message.raw("/hyessentialsx migrate <mod> [merge] - Migrate data from other mods"));
        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("Homes: /sethome [name], /home [name], /homes, /delhome [name]"));
        context.sendMessage(Message.raw("Warps: /setwarp [name], /warp [name], /warps, /delwarp [name]"));
        context.sendMessage(Message.raw("Kits: /kitcreate [name] [cooldown] [maxUses], /kitedit [name] [cooldown] [maxUses] (no args opens item editor), /kit [name], /kits, /kitdelete [name]"));
        context.sendMessage(Message.raw("Chat: /msg <player> <msg>, /r <msg>, /socialspy, /clearchat, /adminchat [msg], /broadcast <msg>"));
        context.sendMessage(Message.raw("Teleport: /spawn, /back, /tpa <player>, /tpahere <player>, /tpahereall, /top, /jumpto, /thru, /rtp"));
        context.sendMessage(Message.raw("Moderation: /mute <player> [time] [reason], /unmute <player>, /ban <player> [reason], /tempban <player> [time] [reason], /unban <player>, /banlist, /ipbans, /vanish [player], /socialspy"));
        context.sendMessage(Message.raw("Misc: /list, /rules, /motd, /near, /whois <player>, /seen <player>, /clearinventory, /repair, /freecam"));
        context.sendMessage(Message.raw("Admin: /import <file>, /hyessentialsx migrate <mod> [merge]"));
        context.sendMessage(Message.raw("========================"));
    }
}

