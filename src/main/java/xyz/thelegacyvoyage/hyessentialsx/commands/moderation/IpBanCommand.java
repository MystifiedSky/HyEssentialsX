package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.IpBanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.IpUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IpBanCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.ipban";

    private final IpBanManager ipBans;
    private final StorageManager storage;

    public IpBanCommand(@Nonnull IpBanManager ipBans, @Nonnull StorageManager storage) {
        super("ipban", "Ban a player's IP address");
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
            Messages.noPerm(context, "/ipban");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            Messages.errKey(context, "ipban.usage", Map.of());
            return;
        }

        String targetName = args.get(0);
        PlayerRef online = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(targetName);
        if (uuid == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        String ip = null;
        if (online != null) {
            ip = IpUtil.extractIp(online.getPacketHandler());
        }
        PlayerDataModel data = storage.getPlayerData(uuid);
        if (ip == null || ip.isBlank()) {
            ip = data.getLastKnownIp();
        } else {
            data.addOrUpdateIp(ip);
            storage.savePlayerDataAsync(uuid, data);
        }

        if (ip == null || ip.isBlank()) {
            Messages.errKey(context, "ipban.not_found", Map.of());
            return;
        }

        String reason = args.size() > 1 ? String.join(" ", args.subList(1, args.size())).trim() : "";
        if (reason.isBlank()) {
            reason = "No reason";
        }

        String actor = resolveActorName(context);
        String displayName = resolveDisplayName(online, uuid, targetName);
        IpBanModel ban = new IpBanModel(ip, displayName, uuid.toString(), actor, reason, System.currentTimeMillis());
        ipBans.ban(ip, ban);

        if (online != null) {
            online.getPacketHandler().disconnect("IP Banned: " + reason);
        }

        Messages.okKey(context, "ipban.banned", Map.of(
                "ip", ip,
                "player", displayName
        ));
    }

    @Nonnull
    private String resolveDisplayName(PlayerRef online, @Nonnull UUID uuid, @Nonnull String fallback) {
        if (online != null) return online.getUsername();
        PlayerDataModel data = storage.getPlayerData(uuid);
        String name = data.getLastKnownName();
        if (name != null && !name.isBlank()) return name;
        return fallback;
    }

    @Nonnull
    private static String resolveActorName(@Nonnull CommandContext context) {
        Object sender = context.sender();
        if (sender == null) return "Console";
        if (sender instanceof PlayerRef playerRef) return playerRef.getUsername();
        try {
            Method method = sender.getClass().getMethod("getName");
            Object value = method.invoke(sender);
            if (value instanceof String name && !name.isBlank()) return name;
        } catch (Exception ignored) {
        }
        return sender.getClass().getSimpleName();
    }
}

