package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreezeManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class UnfreezeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.unfreeze";

    private final FreezeManager freezeManager;
    private final RequiredArg<PlayerRef> targetArg;

    public UnfreezeCommand(@Nonnull FreezeManager freezeManager) {
        super("unfreeze", "Unfreeze a player");
        this.freezeManager = freezeManager;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.targetArg = withRequiredArg("player", "Player to unfreeze", ArgTypes.PLAYER_REF);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/unfreeze");
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        if (!freezeManager.isFrozen(target.getUuid())) {
            Messages.err(context, "That player is not frozen.");
            return;
        }

        freezeManager.unfreeze(target.getUuid());
        Messages.ok(context, "Unfroze " + target.getUsername() + ".");
        Messages.sendPrefixed(target, "&aYou have been unfrozen.");
    }
}
