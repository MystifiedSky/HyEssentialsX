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
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.Random;

public final class RtpCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.rtp";

    private final ConfigManager config;
    private final Random random = new Random();

    public RtpCommand(@Nonnull ConfigManager config) {
        super("rtp", "Random teleport");
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"randomtp", "wild"});
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
            Messages.noPerm(context, "/rtp");
            return;
        }
        if (!config.isRtpEnabled()) {
            Messages.err(context, "RTP is disabled.");
            return;
        }

        Transform transform = playerRef.getTransform();
        if (transform == null) {
            Messages.err(context, "Could not read your position.");
            return;
        }

        Vector3d pos = transform.getPosition();
        int maxDist = Math.max(10, config.getRtpMaxDistance());

        double targetX = pos.getX();
        double targetZ = pos.getZ();
        double targetY = pos.getY();

        for (int i = 0; i < 10; i++) {
            double dx = (random.nextDouble() * 2 - 1) * maxDist;
            double dz = (random.nextDouble() * 2 - 1) * maxDist;
            int blockX = (int) Math.floor(pos.getX() + dx);
            int blockZ = (int) Math.floor(pos.getZ() + dz);
            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
            WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
            if (chunk == null) continue;

            int localX = ChunkUtil.localCoordinate(blockX);
            int localZ = ChunkUtil.localCoordinate(blockZ);
            short height = chunk.getHeight(localX, localZ);

            targetX = blockX + 0.5;
            targetZ = blockZ + 0.5;
            targetY = height + 1.0;
            break;
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                world.getName(),
                targetX, targetY, targetZ,
                0f, 0f
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        Messages.ok(context, "Teleported to a random location.");
    }
}



