package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class TpahereAllCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.tpahereall";

    private final TPManager tpManager;
    private final ConfigManager config;

    public TpahereAllCommand(@Nonnull TPManager tpManager, @Nonnull ConfigManager config) {
        super("tpahereall", "Requests all players to teleport");
        this.tpManager = tpManager;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            Messages.noPerm(context, "/tpahereall");
            return;
        }
        if (!config.isTpaEnabled()) {
            Messages.err(context, "TPA is disabled.");
            return;
        }

        int count = 0;
        for (PlayerRef target : Universe.get().getPlayers()) {
            if (target.getUuid().equals(playerRef.getUuid())) continue;
            if (tpManager.addTpaHereRequest(playerRef.getUuid(), target.getUuid())) {
                count++;
                Messages.send(target,
                        "&#FFFF55" + playerRef.getUsername()
                                + "&#FFFFFF wants you to teleport to them. Type &#FFFF55/tpaaccept&#FFFFFF to accept.");
            }
        }

        Messages.ok(context, "Teleport requests sent to " + count + " player(s).");
    }
}



