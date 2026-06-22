package xyz.thelegacyvoyage.hyessentialsx.commands.tpa;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class TpaCancelCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.tpacancel";

    private final TPManager tpManager;
    private final ConfigManager config;
    private final RequiredArg<PlayerRef> targetArg;

    public TpaCancelCommand(@Nonnull TPManager tpManager, @Nonnull ConfigManager config) {
        super("tpacancel", "Cancel a teleport request you sent");
        this.tpManager = tpManager;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.targetArg = withRequiredArg("player", "Player to cancel for", ArgTypes.PLAYER_REF);
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
            Messages.noPerm(context, "/tpacancel");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.err(context, "TPA is disabled.");
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.err(context, "Player not found.");
            return;
        }

        if (!tpManager.hasTpaRequest(playerRef.getUuid(), target.getUuid())) {
            Messages.err(context, "You don't have a pending request to " + target.getUsername() + ".");
            return;
        }

        tpManager.removeTpaRequest(playerRef.getUuid(), target.getUuid());
        Messages.ok(context, "Canceled your request to " + target.getUsername() + ".");
        Messages.send(target, "&#FFFF55" + playerRef.getUsername() + " canceled their teleport request.");
    }
}



