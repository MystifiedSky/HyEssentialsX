package xyz.thelegacyvoyage.hyessentialsx.commands.back;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;

public final class BackCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.back";

    private final BackManager backManager;
    private final TPManager tpManager;

    public BackCommand(@Nonnull BackManager backManager,
                       @Nonnull TPManager tpManager) {
        super("back", "Return to your last teleport or death location");
        this.backManager = backManager;
        this.tpManager = tpManager;
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
            Messages.noPerm(context, "/back");
            return;
        }

        BackManager.BackPoint back = backManager.peek(playerRef.getUuid());
        if (back == null) {
            Messages.err(context, "Nothing to go back to.");
            return;
        }

        tpManager.cancel(playerRef.getUuid(), null);
        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                back.getWorldName(),
                back.getX(), back.getY(), back.getZ(),
                back.getYaw(), back.getPitch()
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        backManager.consume(playerRef.getUuid());
        Messages.ok(context, "Teleported back.");
    }
}



