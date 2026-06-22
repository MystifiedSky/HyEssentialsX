package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.MuteManager;
import xyz.thelegacyvoyage.hyessentialsx.models.MuteModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;

public final class MuteCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.mute";

    private final MuteManager muteManager;
    private final RequiredArg<PlayerRef> targetArg;
    private final OptionalArg<String> timeArg;
    private final OptionalArg<List<String>> reasonArg;

    public MuteCommand(@Nonnull MuteManager muteManager) {
        super("mute", "Mutes a player");
        this.muteManager = muteManager;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"silence"});
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
        this.timeArg = withOptionalArg("time", "Duration (e.g. 30d)", ArgTypes.STRING);
        this.reasonArg = withListOptionalArg("reason", "Reason", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/mute");
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        long expiresAt = 0L;
        String reason = null;

        String timeToken = context.provided(timeArg) ? context.get(timeArg) : null;
        if (timeToken == null || timeToken.isBlank()) {
            timeToken = CommandInputUtil.getArg(context, 1);
        }
        if (timeToken != null && !timeToken.isBlank()) {
            long seconds = TimeUtil.parseDurationSeconds(timeToken);
            if (seconds >= 0) {
                expiresAt = System.currentTimeMillis() + (seconds * 1000L);
            } else {
                reason = timeToken;
            }
        }

        if (context.provided(reasonArg)) {
            List<String> parts = context.get(reasonArg);
            String tail = String.join(" ", parts).trim();
            if (!tail.isBlank()) {
                reason = (reason == null || reason.isBlank()) ? tail : (reason + " " + tail);
            }
        } else {
            List<String> args = CommandInputUtil.getArgs(context);
            if (args.size() > 2) {
                String tail = String.join(" ", args.subList(2, args.size())).trim();
                if (!tail.isBlank()) {
                    reason = (reason == null || reason.isBlank()) ? tail : (reason + " " + tail);
                }
            }
        }

        String actor = resolveActorName(context);

        muteManager.mute(target.getUuid(), new MuteModel(
                target.getUsername(),
                actor,
                (reason == null || reason.isBlank())
                        ? Messages.tr(null, "reason.none", java.util.Map.of())
                        : reason,
                expiresAt,
                System.currentTimeMillis()
        ));

        Messages.okKey(context, "mute.success", java.util.Map.of("player", target.getUsername()));
    }

    private static String resolveActorName(@Nonnull CommandContext context) {
        Object sender = context.sender();
        if (sender == null) return Messages.tr(null, "actor.console", java.util.Map.of());
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




