package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.camera.SetFlyCameraMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreecamManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.Map;

public final class FreecamCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.freecam";
    private static final String OTHER_PERMISSION = "hyessentialsx.freecam.other";

    private final FreecamManager freecamManager;
    private final OptionalArg<PlayerRef> targetArg;

    public FreecamCommand(@Nonnull FreecamManager freecamManager) {
        super("freecam", "Toggles free camera");
        this.freecamManager = freecamManager;
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
            Messages.noPerm(context, "/freecam");
            return;
        }

        PlayerRef target = context.provided(targetArg) ? context.get(targetArg) : playerRef;
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (!isSelf && !context.sender().hasPermission(OTHER_PERMISSION)) {
            Messages.noPerm(context, "/freecam " + target.getUsername());
            return;
        }

        boolean activate = freecamManager.toggle(target.getUuid());
        SetFlyCameraMode packet = new SetFlyCameraMode(activate);
        target.getPacketHandler().write((Packet) packet);
        if (isSelf) {
            Messages.ok(context, activate ? "Freecam enabled." : "Freecam disabled.");
        } else {
            Messages.ok(context, (activate ? "Freecam enabled for " : "Freecam disabled for ") + target.getUsername() + ".");
            Messages.sendPrefixed(target, activate
                    ? "Freecam enabled by " + playerRef.getUsername() + "."
                    : "Freecam disabled by " + playerRef.getUsername() + ".");
        }
    }
}




