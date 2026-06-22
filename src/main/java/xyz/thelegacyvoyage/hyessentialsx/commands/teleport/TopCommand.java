package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.Map;

public final class TopCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.top";
    private static final String OTHER_PERMISSION = "hyessentialsx.top.other";

    private final BackManager backManager;
    private final OptionalArg<PlayerRef> targetArg;

    public TopCommand(@Nonnull BackManager backManager) {
        super("top", "Teleports to highest block");
        this.backManager = backManager;
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
            Messages.noPerm(context, "/top");
            return;
        }

        PlayerRef target = context.provided(targetArg) ? context.get(targetArg) : playerRef;
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (!isSelf && !context.sender().hasPermission(OTHER_PERMISSION)) {
            Messages.noPerm(context, "/top " + target.getUsername());
            return;
        }

        World targetWorld = resolveTargetWorld(target, world);
        if (targetWorld == null) {
            Messages.err(context, "World not loaded.");
            return;
        }

        Transform transform = target.getTransform();
        if (transform == null) {
            Messages.err(context, "Could not read target position.");
            return;
        }

        Vector3d pos = transform.getPosition();
        int blockX = (int) Math.floor(pos.getX());
        int blockZ = (int) Math.floor(pos.getZ());
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        WorldChunk chunk = targetWorld.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            Messages.err(context, "Chunk not loaded.");
            return;
        }

        if (transform.getPosition() != null) {
            com.hypixel.hytale.math.vector.Vector3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            backManager.recordLocation(
                    target.getUuid(),
                    targetWorld.getName(),
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

        Ref<EntityStore> targetRef = target.getReference();
        Store<EntityStore> targetStore = targetRef != null ? targetRef.getStore() : null;
        if (targetRef == null || targetStore == null) {
            Messages.err(context, "Could not access target.");
            return;
        }

        String err = TeleportationUtil.teleportToLocation(
                targetStore,
                targetRef,
                targetWorld.getName(),
                pos.getX(), targetY, pos.getZ(),
                0f, 0f
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        if (isSelf) {
            Messages.ok(context, "Teleported to top.");
        } else {
            Messages.ok(context, "Teleported " + target.getUsername() + " to top.");
            Messages.sendPrefixed(target, "You were teleported to the top by " + playerRef.getUsername() + ".");
        }
    }

    private World resolveTargetWorld(@Nonnull PlayerRef target, @Nonnull World fallback) {
        try {
            Ref<EntityStore> targetRef = target.getReference();
            Store<EntityStore> targetStore = targetRef != null ? targetRef.getStore() : null;
            if (targetStore != null && targetStore.getExternalData().getWorld() != null) {
                return targetStore.getExternalData().getWorld();
            }
        } catch (Exception ignored) {
        }
        if (target.getWorldUuid() != null) {
            World byUuid = Universe.get().getWorld(target.getWorldUuid());
            if (byUuid != null) return byUuid;
        }
        return fallback;
    }
}




