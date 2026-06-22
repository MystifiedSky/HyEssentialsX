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

public final class TpaDenyCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.tpadeny";

    private final TPManager tpManager;
    private final ConfigManager config;
    private final RequiredArg<PlayerRef> requesterArg;

    public TpaDenyCommand(@Nonnull TPManager tpManager, @Nonnull ConfigManager config) {
        super("tpadeny", "Deny a teleport request");
        this.tpManager = tpManager;
        this.config = config;
        this.setPermissionGroup(null);
        this.addAliases(new String[]{"tpdeny"});
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.requesterArg = withRequiredArg("player", "Player to deny", ArgTypes.PLAYER_REF);
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
            Messages.noPerm(context, "/tpadeny");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.errKey(context, "tpa.disabled", java.util.Map.of());
            return;
        }

        PlayerRef requester = context.get(requesterArg);
        if (requester == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        if (!tpManager.hasTpaRequest(requester.getUuid(), playerRef.getUuid())) {
            Messages.errKey(context, "tpa.deny.no_pending", java.util.Map.of("player", requester.getUsername()));
            return;
        }

        tpManager.removeTpaRequest(requester.getUuid(), playerRef.getUuid());
        Messages.okKey(context, "tpa.denied", java.util.Map.of("player", requester.getUsername()));
        Messages.sendKey(requester, "tpa.denied_by", java.util.Map.of("player", playerRef.getUsername()));
    }
}




