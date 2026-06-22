package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.IpHistoryModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.IpUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IpHistoryCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.iphistory";

    private final StorageManager storage;

    public IpHistoryCommand(@Nonnull StorageManager storage) {
        super("iphistory", "Show a player's IP history");
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
            Messages.noPerm(context, "/iphistory");
            return;
        }

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            Messages.err(context, "Usage: /iphistory <player>");
            return;
        }

        String targetName = args.get(0);
        PlayerRef online = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(targetName);
        if (uuid == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        PlayerDataModel data = storage.getPlayerData(uuid);
        if (online != null) {
            String ip = IpUtil.extractIp(online.getPacketHandler());
            if (ip != null && !ip.isBlank()) {
                data.addOrUpdateIp(ip);
                storage.savePlayerDataAsync(uuid, data);
            }
        }

        String displayName = resolveDisplayName(online, data, targetName);
        List<IpHistoryModel> entries = new ArrayList<>();
        for (IpHistoryModel entry : data.getIpHistory()) {
            if (entry == null || entry.getIp() == null || entry.getIp().isBlank()) continue;
            entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(IpHistoryModel::getLastUsed).reversed());

        context.sendMessage(Messages.m("&8----------------------------------------"));
        context.sendMessage(Messages.m("&aIP HISTORY &7- &f" + displayName));
        String currentIp = data.getCurrentIp();
        context.sendMessage(Messages.m("&aCurrent IP: &f" + (currentIp == null || currentIp.isBlank() ? "Unknown" : currentIp)));

        if (entries.isEmpty()) {
            context.sendMessage(Messages.m("&cNo IP history found."));
            context.sendMessage(Messages.m("&8----------------------------------------"));
            return;
        }

        int index = 1;
        for (IpHistoryModel entry : entries) {
            String timestamp = formatTimestamp(entry.getLastUsed());
            String ago = formatAgo(entry.getLastUsed());
            context.sendMessage(Messages.m("&a" + index + ". &f" + entry.getIp() + " &7- " + timestamp + ago));
            index++;
        }
        context.sendMessage(Messages.m("&8----------------------------------------"));
    }

    @Nonnull
    private static String resolveDisplayName(@Nullable PlayerRef online,
                                             @Nonnull PlayerDataModel data,
                                             @Nonnull String fallback) {
        if (online != null) return online.getUsername();
        String name = data.getLastKnownName();
        if (name != null && !name.isBlank()) return name;
        return fallback;
    }

    @Nonnull
    private static String formatTimestamp(long millis) {
        if (millis <= 0L) return "Never";
        Instant instant = Instant.ofEpochMilli(millis);
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    @Nonnull
    private static String formatAgo(long millis) {
        if (millis <= 0L) return "";
        long diff = Math.max(0, System.currentTimeMillis() - millis);
        String ago = TimeUtil.formatDurationSeconds(diff / 1000L);
        return " &7(" + ago + " ago)";
    }
}
