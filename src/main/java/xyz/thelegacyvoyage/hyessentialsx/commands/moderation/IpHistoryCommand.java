package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.IpHistoryModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
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
        this.setPermissionGroups();
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/iphistory");
            return;
        }

        PlayerRef viewer = CommandSenderUtil.resolvePlayer(context);

        List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            Messages.errKey(context, "iphistory.usage", Map.of());
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

        context.sendMessage(Messages.m(Messages.tr(viewer, "info.separator", Map.of())));
        context.sendMessage(Messages.m(Messages.tr(viewer, "iphistory.header", Map.of(
                "player", displayName
        ))));
        String currentIp = data.getCurrentIp();
        String ipText = (currentIp == null || currentIp.isBlank())
                ? Messages.tr(viewer, "info.unknown", Map.of())
                : currentIp;
        context.sendMessage(Messages.m(Messages.tr(viewer, "iphistory.current_ip", Map.of(
                "ip", ipText
        ))));

        if (entries.isEmpty()) {
            context.sendMessage(Messages.m(Messages.tr(viewer, "iphistory.none", Map.of())));
            context.sendMessage(Messages.m(Messages.tr(viewer, "info.separator", Map.of())));
            return;
        }

        int index = 1;
        for (IpHistoryModel entry : entries) {
            String timestamp = formatTimestamp(viewer, entry.getLastUsed());
            String ago = formatAgo(viewer, entry.getLastUsed());
            context.sendMessage(Messages.m(Messages.tr(viewer, "iphistory.entry", Map.of(
                    "index", String.valueOf(index),
                    "ip", entry.getIp(),
                    "time", timestamp,
                    "ago", ago
            ))));
            index++;
        }
        context.sendMessage(Messages.m(Messages.tr(viewer, "info.separator", Map.of())));
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
    private static String formatTimestamp(@Nullable PlayerRef viewer, long millis) {
        if (millis <= 0L) return Messages.tr(viewer, "time.never", Map.of());
        Instant instant = Instant.ofEpochMilli(millis);
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    @Nonnull
    private static String formatAgo(@Nullable PlayerRef viewer, long millis) {
        if (millis <= 0L) return "";
        long diff = Math.max(0, System.currentTimeMillis() - millis);
        String ago = TimeUtil.formatDurationSeconds(diff / 1000L);
        return Messages.tr(viewer, "time.ago", Map.of("time", ago));
    }
}
