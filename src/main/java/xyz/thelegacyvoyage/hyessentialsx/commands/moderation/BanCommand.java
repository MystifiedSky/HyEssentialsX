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
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.BanModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BanCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.ban";

    private final BanManager banManager;
    private final StorageManager storage;
    private final RequiredArg<String> nameArg;
    private final OptionalArg<List<String>> reasonArg;

    public BanCommand(@Nonnull BanManager banManager, @Nonnull StorageManager storage) {
        super("ban", "Permanently bans a player");
        this.banManager = banManager;
        this.storage = storage;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
        this.reasonArg = withListOptionalArg("reason", "Reason", ArgTypes.STRING);
        this.addAliases(new String[]{"permban"});
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/ban");
            return;
        }

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.errKey(context, "player.name_required", Map.of());
            return;
        }

        PlayerRef online = Universe.get().getPlayerByUsername(name, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(name);
        if (uuid == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        String reason = null;
        if (context.provided(reasonArg)) {
            List<String> parts = context.get(reasonArg);
            reason = String.join(" ", parts).trim();
        } else {
            List<String> args = CommandInputUtil.getArgs(context);
            if (args.size() > 1) {
                reason = String.join(" ", args.subList(1, args.size())).trim();
            }
        }

        String actor = resolveActorName(context);
        String finalReason = (reason == null || reason.isBlank())
                ? Messages.tr(null, "reason.none", Map.of())
                : reason;

        banManager.ban(uuid, new BanModel(
                name,
                actor,
                finalReason,
                0L,
                System.currentTimeMillis()
        ));

        if (online != null) {
            String reasonText = (reason == null || reason.isBlank())
                    ? Messages.tr(online, "reason.none", Map.of())
                    : reason;
            String timeText = Messages.tr(online, "ban.permanent", Map.of());
            online.getPacketHandler().disconnect(Messages.tr(online, "ban.kick", Map.of(
                    "reason", reasonText,
                    "time", timeText
            )));
        }

        Messages.okKey(context, "ban.temp.success", Map.of(
                "player", name,
                "time", Messages.tr(null, "ban.permanent", Map.of())
        ));
    }

    private static String resolveActorName(@Nonnull CommandContext context) {
        Object sender = context.sender();
        if (sender == null) return Messages.tr(null, "actor.console", Map.of());
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
