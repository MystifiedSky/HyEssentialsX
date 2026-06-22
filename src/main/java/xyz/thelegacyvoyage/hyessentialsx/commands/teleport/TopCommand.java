package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;

public final class TopCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.top";

    private final BackManager backManager;

    public TopCommand(@Nonnull BackManager backManager) {
        super("top", "Teleports to highest block");
        this.backManager = backManager;
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
            Messages.noPerm(context, "/top");
            return;
        }

        Transform transform = playerRef.getTransform();
        if (transform == null) {
            Messages.err(context, "Could not read your position.");
            return;
        }

        Vector3d pos = transform.getPosition();
        int blockX = (int) Math.floor(pos.getX());
        int blockZ = (int) Math.floor(pos.getZ());
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            Messages.err(context, "Chunk not loaded.");
            return;
        }

        if (transform.getPosition() != null) {
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            backManager.recordLocation(
                    playerRef.getUuid(),
                    world.getName(),
                    transform.getPosition().getX(),
                    transform.getPosition().getY(),
                    transform.getPosition().getZ(),
                    startYaw, startPitch
            );
        }

        int localX = ChunkUtil.localCoordinate(blockX);
        int localZ = ChunkUtil.localCoordinate(blockZ);
        short height = chunk.getHeight(localX, localZ);
        double targetY = height + 1.0;

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                world.getName(),
                pos.getX(), targetY, pos.getZ(),
                0f, 0f
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        Messages.ok(context, "Teleported to top.");
    }
}




