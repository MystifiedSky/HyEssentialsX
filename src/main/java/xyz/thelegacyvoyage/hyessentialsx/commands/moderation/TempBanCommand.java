package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.BanManager;
import xyz.thelegacyvoyage.hyessentialsx.models.BanModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class TempBanCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.tempban";

    private final BanManager banManager;
    private final StorageManager storage;
    private final RequiredArg<String> nameArg;
    private final RequiredArg<String> timeArg;
    private final OptionalArg<List<String>> reasonArg;

    public TempBanCommand(@Nonnull BanManager banManager, @Nonnull StorageManager storage) {
        super("tempban", "Temporarily bans a player");
        this.banManager = banManager;
        this.storage = storage;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
        this.timeArg = withRequiredArg("time", "Duration (e.g. 30d)", ArgTypes.STRING);
        this.reasonArg = withListOptionalArg("reason", "Reason", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/tempban");
            return;
        }

        String name = context.get(nameArg);
        long seconds = TimeUtil.parseDurationSeconds(context.get(timeArg));
        if (seconds <= 0) {
            Messages.err(context, "Invalid time. Use 10m/2h/3d/1y.");
            return;
        }

        PlayerRef online = Universe.get().getPlayerByUsername(name, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(name);
        if (uuid == null) {
            Messages.err(context, "Player not found.");
            return;
        }

        String reason = null;
        if (context.provided(reasonArg)) {
            List<String> parts = context.get(reasonArg);
            reason = String.join(" ", parts).trim();
        }

        long expiresAt = System.currentTimeMillis() + (seconds * 1000L);
        String actor = resolveActorName(context);
        banManager.ban(uuid, new BanModel(
                name,
                actor,
                (reason == null || reason.isBlank()) ? "No reason" : reason,
                expiresAt,
                System.currentTimeMillis()
        ));

        if (online != null) {
            online.getPacketHandler().disconnect("Banned: " + (reason != null ? reason : "No reason")
                    + " (" + TimeUtil.formatDurationSeconds(seconds) + ")");
        }

        Messages.ok(context, "Banned " + name + " for " + TimeUtil.formatDurationSeconds(seconds) + ".");
    }

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




