package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.collision.WorldUtil;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ThruCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.thru";
    private static final int MAX_DISTANCE = 100;
    private static final double STEP_SIZE = 0.05;

    private final BackManager backManager;

    public ThruCommand(@Nonnull BackManager backManager) {
        super("thru", "Teleport through the wall you're looking at");
        this.backManager = backManager;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"through", "wallthrough"});
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/thru");
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            Messages.errKey(context, "error.position_unavailable", Map.of());
            return;
        }
        if (headRotation == null || headRotation.getRotation() == null) {
            Messages.errKey(context, "error.view_direction_unavailable", Map.of());
            return;
        }

        Vector3d eyePosition = new org.joml.Vector3d(transform.getPosition()).add(0.0, 1.59375, 0.0);
        float pitch = headRotation.getRotation().x();
        float yaw = headRotation.getRotation().y();

        Vector3d direction = getDirectionFromRotation(pitch, yaw);
        Vector3d destination = findDestinationBeyondWall(world, eyePosition, direction);
        if (destination == null) {
            Messages.errKey(context, "thru.no_safe_spot", Map.of());
            return;
        }

        if (transform.getPosition() != null) {
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            float startYaw = (rot != null) ? rot.y() : 0f;
            float startPitch = (rot != null) ? rot.x() : 0f;
            backManager.recordLocation(
                    playerRef.getUuid(),
                    world.getName(),
                    transform.getPosition().x(),
                    transform.getPosition().y(),
                    transform.getPosition().z(),
                    startYaw,
                    startPitch
            );
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                world.getName(),
                destination.x(),
                destination.y(),
                destination.z(),
                yaw,
                pitch
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        Messages.okKey(context, "thru.success", Map.of());
    }

    @Nonnull
    private Vector3d getDirectionFromRotation(float pitch, float yaw) {
        double x = -Math.cos(pitch) * Math.sin(yaw);
        double y = Math.sin(pitch);
        double z = -Math.cos(pitch) * Math.cos(yaw);
        return new Vector3d(x, y, z);
    }

    @Nullable
    private Vector3d findDestinationBeyondWall(@Nonnull World world,
                                               @Nonnull Vector3d start,
                                               @Nonnull Vector3d direction) {
        Set<Long> visited = new HashSet<>();
        boolean enteredSolid = false;
        int[] firstSolid = null;

        for (double distance = 0.0; distance <= MAX_DISTANCE; distance += STEP_SIZE) {
            Vector3d current = start.add(
                    direction.x() * distance,
                    direction.y() * distance,
                    direction.z() * distance
            );
            int blockX = (int) Math.floor(current.x());
            int blockY = (int) Math.floor(current.y());
            int blockZ = (int) Math.floor(current.z());

            long key = ((long) blockX << 40) | ((long) (blockY & 0xFFFFF) << 20) | (long) (blockZ & 0xFFFFF);
            if (!visited.add(key)) {
                continue;
            }

            boolean solid = isSolidBlock(world, blockX, blockY, blockZ);
            if (!enteredSolid) {
                if (solid) {
                    enteredSolid = true;
                    firstSolid = new int[]{blockX, blockY, blockZ};
                }
                continue;
            }

            if (!solid) {
                Vector3d safe = findSafeLandingNear(world, blockX, blockY, blockZ);
                if (safe != null) {
                    return safe;
                }
            }
        }

        if (firstSolid == null) {
            return null;
        }
        return findSafeLandingNear(world, firstSolid[0], firstSolid[1] + 1, firstSolid[2]);
    }

    @Nullable
    private Vector3d findSafeLandingNear(@Nonnull World world, int x, int y, int z) {
        for (int dy = -3; dy <= 4; dy++) {
            int testY = y + dy;
            if (testY < 1 || testY > 319) continue;
            if (isSafeStandingSpot(world, x, testY, z)) {
                return new Vector3d(x + 0.5, testY, z + 0.5);
            }
        }
        return null;
    }

    private boolean isSafeStandingSpot(@Nonnull World world, int x, int y, int z) {
        return isSolidBlock(world, x, y - 1, z)
                && isClearBlock(world, x, y, z)
                && isClearBlock(world, x, y + 1, z);
    }

    private boolean isClearBlock(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y > 319) return false;
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) return false;
        BlockType type = chunk.getBlockType(x, y, z);
        if (type == null) return true;
        return type.getMaterial() != BlockMaterial.Solid && !isLiquidAt(chunk, x, y, z);
    }

    private boolean isSolidBlock(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y > 319) return false;
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) return false;
        BlockType type = chunk.getBlockType(x, y, z);
        return type != null && type.getMaterial() == BlockMaterial.Solid;
    }

    private boolean isLiquidAt(@Nonnull WorldChunk chunk, int x, int y, int z) {
        try {
            Ref<ChunkStore> chunkRef = chunk.getReference();
            if (chunkRef == null) {
                return false;
            }
            ChunkColumn chunkColumn = chunk.getWorld().getChunkStore().getStore()
                    .getComponent(chunkRef, ChunkColumn.getComponentType());
            if (chunkColumn == null) {
                return false;
            }
            return WorldUtil.getFluidIdAtPosition(chunk.getWorld().getChunkStore().getStore(), chunkColumn, x, y, z) != 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
