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
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.Map;

public final class TphereCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.tphere";

    private final RequiredArg<PlayerRef> targetArg;

    public TphereCommand() {
        super("tphere", "Teleports a player to you");
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.targetArg = withRequiredArg("player", "Player to teleport", ArgTypes.PLAYER_REF);
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
            Messages.noPerm(context, "/tphere");
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        if (target.getUuid().equals(playerRef.getUuid())) {
            Messages.errKey(context, "tphere.self", Map.of());
            return;
        }

        Ref<EntityStore> targetRef = target.getReference();
        Store<EntityStore> targetStore = targetRef.getStore();

        String err = TeleportationUtil.teleportToPlayer(targetStore, targetRef, playerRef);
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        Messages.okKey(context, "tphere.success", Map.of("player", target.getUsername()));
        Messages.sendPrefixedKey(target, "tphere.target", Map.of("player", playerRef.getUsername()));
    }
}
