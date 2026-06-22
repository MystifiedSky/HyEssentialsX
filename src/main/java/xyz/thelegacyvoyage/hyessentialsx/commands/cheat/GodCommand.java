package xyz.thelegacyvoyage.hyessentialsx.commands.cheat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class GodCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.god";
    private static final String OTHERS_PERMISSION = "hyessentialsx.god.others";

    private final GodManager godManager;
    private final OptionalArg<PlayerRef> targetArg;

    public GodCommand(@Nonnull GodManager godManager) {
        super("god", "Toggle invulnerability");
        this.godManager = godManager;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.targetArg = withOptionalArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            Messages.noPerm(context, "/god");
            return;
        }

        PlayerRef target = context.provided(targetArg) ? context.get(targetArg) : playerRef;
        if (target == null) {
            Messages.errKey(context, "error.player_only", java.util.Map.of());
            return;
        }

        if (!playerRef.getUuid().equals(target.getUuid())
                && !context.sender().hasPermission(OTHERS_PERMISSION)) {
            Messages.noPerm(context, "/god");
            return;
        }

        boolean nowEnabled = godManager.toggle(target.getUuid());

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (isSelf) {
            Messages.ok(context, nowEnabled ? "God mode enabled." : "God mode disabled.");
        } else {
            Messages.ok(context, (nowEnabled ? "God mode enabled for " : "God mode disabled for ") + target.getUsername() + ".");
            Messages.sendPrefixed(target, nowEnabled
                    ? "God mode enabled by " + playerRef.getUsername() + "."
                    : "God mode disabled by " + playerRef.getUsername() + ".");
        }
    }
}



