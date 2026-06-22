package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.IpBanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.IpUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UnipBanCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.ipban";

    private final IpBanManager ipBans;
    private final StorageManager storage;

    public UnipBanCommand(@Nonnull IpBanManager ipBans, @Nonnull StorageManager storage) {
        super("unipban", "Unban an IP address");
        this.ipBans = ipBans;
        this.storage = storage;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/unipban");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            Messages.errKey(context, "unipban.usage", Map.of());
            return;
        }

        String target = args.get(0);
        String ip = null;
        if (isLikelyIp(target)) {
            ip = IpUtil.normalizeIp(target);
        }

        if (ip == null) {
            PlayerRef online = Universe.get().getPlayerByUsername(target, NameMatching.EXACT_IGNORE_CASE);
            UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(target);
            if (uuid == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            if (online != null) {
                ip = IpUtil.extractIp(online.getPacketHandler());
            }
            PlayerDataModel data = storage.getPlayerData(uuid);
            if (ip == null || ip.isBlank()) {
                ip = data.getLastKnownIp();
            }
            if (ip == null || ip.isBlank()) {
                Messages.errKey(context, "ipban.not_found", Map.of());
                return;
            }
        }

        if (!ipBans.unban(ip)) {
            Messages.errKey(context, "ipban.not_banned", Map.of());
            return;
        }

        Messages.okKey(context, "ipban.unbanned", Map.of("ip", ip));
    }

    private static boolean isLikelyIp(@Nonnull String raw) {
        return raw.contains(".") || raw.contains(":") || raw.startsWith("/") || raw.startsWith("[");
    }
}

