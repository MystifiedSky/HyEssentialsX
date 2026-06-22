package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public final class JumpToCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.jumpto";
    private static final String BYPASS_PERMISSION = "hyessentialsx.jumpto.bypass";
    private static final int MAX_DISTANCE = 100;
    private static final double STEP_SIZE = 0.005;

    private final CommandCooldownManager cooldowns;

    public JumpToCommand(@Nonnull CommandCooldownManager cooldowns) {
        super("jumpto", "Teleports to target block");
        this.cooldowns = cooldowns;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"junp", "j", "jump"});
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
            Messages.noPerm(context, "/jumpto");
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.JUMPTO, "/jumpto", BYPASS_PERMISSION)) {
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (transform == null) {
            Messages.err(context, "Could not read your position.");
            return;
        }
        if (headRotation == null) {
            Messages.err(context, "Could not read your view direction.");
            return;
        }

        Vector3d eyePosition = transform.getPosition().clone().add(0.0, 1.59375, 0.0);
        float pitch = headRotation.getRotation().getX();
        float yaw = headRotation.getRotation().getY();

        Vector3d direction = getDirectionFromRotation(pitch, yaw);
        Vector3d target = findTargetBlock(world, eyePosition, direction);
        if (target == null) {
            Messages.err(context, "No block in range.");
            return;
        }

        String err = TeleportationUtil.teleportToLocation(
                store,
                ref,
                world.getName(),
                target.getX(), target.getY(), target.getZ(),
                yaw, pitch
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.JUMPTO);
        Messages.ok(context, "Jumped to target.");
    }

    @Nonnull
    private Vector3d getDirectionFromRotation(float pitch, float yaw) {
        double x = -Math.cos(pitch) * Math.sin(yaw);
        double y = Math.sin(pitch);
        double z = -Math.cos(pitch) * Math.cos(yaw);
        return new Vector3d(x, y, z);
    }

    @Nullable
    private Vector3d findTargetBlock(@Nonnull World world, @Nonnull Vector3d start, @Nonnull Vector3d direction) {
        Set<Long> checked = new HashSet<>();
        for (double distance = 0.0; distance <= MAX_DISTANCE; distance += STEP_SIZE) {
            Vector3d current = start.add(
                    direction.getX() * distance,
                    direction.getY() * distance,
                    direction.getZ() * distance
            );

            int blockX = (int) Math.floor(current.getX());
            int blockY = (int) Math.floor(current.getY());
            int blockZ = (int) Math.floor(current.getZ());

            long key = ((long) blockX << 40) | ((long) (blockY & 0xFFFFF) << 20) | (long) (blockZ & 0xFFFFF);
            if (!checked.add(key)) continue;

            if (isSolidBlock(world, blockX, blockY, blockZ)) {
                return findSafePositionAbove(world, blockX, blockY, blockZ);
            }
        }
        return null;
    }

    private boolean isSolidBlock(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y > 319) return false;
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) return false;
        BlockType type = chunk.getBlockType(x, y, z);
        return type != null && type.getMaterial() == BlockMaterial.Solid;
    }

    @Nullable
    private Vector3d findSafePositionAbove(@Nonnull World world, int x, int y, int z) {
        for (int checkY = y + 1; checkY <= 319; checkY++) {
            if (!isSolidBlock(world, x, checkY, z) && !isSolidBlock(world, x, checkY + 1, z)) {
                return new Vector3d(x + 0.5, checkY, z + 0.5);
            }
        }
        if (!isSolidBlock(world, x, y + 1, z) && !isSolidBlock(world, x, y + 2, z)) {
            return new Vector3d(x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }
}
