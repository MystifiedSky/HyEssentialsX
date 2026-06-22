package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.camera.SetFlyCameraMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreecamManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class FreecamCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.freecam";

    private final FreecamManager freecamManager;

    public FreecamCommand(@Nonnull FreecamManager freecamManager) {
        super("freecam", "Toggles free camera");
        this.freecamManager = freecamManager;
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
            Messages.noPerm(context, "/freecam");
            return;
        }

        boolean activate = freecamManager.toggle(playerRef.getUuid());
        SetFlyCameraMode packet = new SetFlyCameraMode(activate);
        playerRef.getPacketHandler().write((Packet) packet);
        Messages.ok(context, activate ? "Freecam enabled." : "Freecam disabled.");
    }
}



