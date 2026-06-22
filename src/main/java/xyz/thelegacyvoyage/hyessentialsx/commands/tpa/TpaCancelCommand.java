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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/tpacancel");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.errKey(context, "tpa.disabled", java.util.Map.of());
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        if (!tpManager.hasTpaRequest(playerRef.getUuid(), target.getUuid())) {
            Messages.errKey(context, "tpa.cancel.no_pending", java.util.Map.of("player", target.getUsername()));
            return;
        }

        tpManager.removeTpaRequest(playerRef.getUuid(), target.getUuid());
        Messages.okKey(context, "tpa.cancelled", java.util.Map.of("player", target.getUsername()));
        Messages.sendKey(target, "tpa.cancelled_by", java.util.Map.of("player", playerRef.getUsername()));
    }
}




