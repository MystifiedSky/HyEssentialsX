package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class KickAllCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.kickall";
    private final RequiredArg<List<String>> reasonArg;

    public KickAllCommand() {
        super("kickall", "Kick all online players except the executor");
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.reasonArg = withListRequiredArg("reason", "Kick reason", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/kickall");
            return;
        }
        PlayerRef actor = CommandSenderUtil.resolvePlayer(context);
        String reason = String.join(" ", context.get(reasonArg)).trim();
        if (reason.isBlank()) {
            reason = "Kicked by staff.";
        }
        int count = 0;
        for (PlayerRef target : Universe.get().getPlayers()) {
            if (target == null) continue;
            if (actor != null && actor.getUuid().equals(target.getUuid())) continue;
            try {
                target.getPacketHandler().disconnect(Messages.m(reason));
                count++;
            } catch (Exception ignored) {
            }
        }
        Messages.okKey(context, "kickall.done", Map.of("count", String.valueOf(count)));
    }
}
