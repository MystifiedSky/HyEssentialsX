package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.IgnoreManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class UnignoreCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.ignore";

    private final IgnoreManager ignoreManager;
    private final RequiredArg<PlayerRef> targetArg;

    public UnignoreCommand(@Nonnull IgnoreManager ignoreManager) {
        super("unignore", "Stop ignoring private messages from a player");
        this.ignoreManager = ignoreManager;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/unignore");
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }
        if (playerRef.getUuid().equals(target.getUuid())) {
            Messages.errKey(context, "ignore.self", Map.of());
            return;
        }

        if (ignoreManager.unignore(playerRef.getUuid(), target.getUuid())) {
            Messages.okKey(context, "ignore.disabled", Map.of("player", target.getUsername()));
        } else {
            Messages.warnKey(context, "ignore.not_ignoring", Map.of("player", target.getUsername()));
        }
    }
}
