package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MuteManager;
import xyz.thelegacyvoyage.hyessentialsx.models.MuteModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.StaffActionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;

public final class MuteCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.mute";

    private final MuteManager muteManager;
    private final StorageManager storage;
    private final RequiredArg<PlayerRef> targetArg;

    public MuteCommand(@Nonnull MuteManager muteManager, @Nonnull StorageManager storage) {
        super("mute", "Mutes a player");
        this.muteManager = muteManager;
        this.storage = storage;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"silence"});
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
        this.addUsageVariant(new MuteTimedCommand());
        this.addUsageVariant(new MuteTimedReasonCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/mute");
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        mutePlayer(context, target, null, List.of());
    }

    private void mutePlayer(@Nonnull CommandContext context,
                            @Nonnull PlayerRef target,
                            String timeToken,
                            @Nonnull List<String> reasonParts) {
        long expiresAt = 0L;
        String reason = null;
        if (timeToken != null && !timeToken.isBlank()) {
            long seconds = TimeUtil.parseDurationSeconds(timeToken);
            if (seconds >= 0) {
                expiresAt = System.currentTimeMillis() + (seconds * 1000L);
            } else {
                reason = timeToken;
            }
        }

        String tail = String.join(" ", reasonParts).trim();
        if (!tail.isBlank()) {
            reason = (reason == null || reason.isBlank()) ? tail : (reason + " " + tail);
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
        StaffActionUtil.log(storage, actor, "mute", target.getUuid(), target.getUsername(),
                (reason == null || reason.isBlank()) ? Messages.tr(null, "reason.none", java.util.Map.of()) : reason);

        Messages.okKey(context, "mute.success", java.util.Map.of("player", target.getUsername()));
    }

    private final class MuteTimedCommand extends CommandBase {
        private final RequiredArg<PlayerRef> targetArg;
        private final RequiredArg<String> timeArg;

        private MuteTimedCommand() {
            super("Mutes a player with a duration or reason token");
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.timeArg = withRequiredArg("time", "Duration (e.g. 30d) or reason", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/mute");
                return;
            }
            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", java.util.Map.of());
                return;
            }
            mutePlayer(context, target, context.get(timeArg), List.of());
        }
    }

    private final class MuteTimedReasonCommand extends CommandBase {
        private final RequiredArg<PlayerRef> targetArg;
        private final RequiredArg<String> timeArg;
        private final RequiredArg<List<String>> reasonArg;

        private MuteTimedReasonCommand() {
            super("Mutes a player with a duration and reason");
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.timeArg = withRequiredArg("time", "Duration (e.g. 30d)", ArgTypes.STRING);
            this.reasonArg = withListRequiredArg("reason", "Reason", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/mute");
                return;
            }
            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", java.util.Map.of());
                return;
            }
            List<String> parts = context.get(reasonArg);
            mutePlayer(context, target, context.get(timeArg), parts == null ? List.of() : parts);
        }
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




