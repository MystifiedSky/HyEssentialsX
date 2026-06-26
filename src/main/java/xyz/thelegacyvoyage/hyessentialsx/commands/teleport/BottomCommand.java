package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public final class BottomCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.bottom";
    private static final String OTHER_PERMISSION = "hyessentialsx.bottom.other";
    private static final String BYPASS_PERMISSION = "hyessentialsx.bottom.bypass";

    private final BackManager backManager;
    private final CommandCooldownManager cooldowns;

    public BottomCommand(@Nonnull BackManager backManager,
                         @Nonnull CommandCooldownManager cooldowns) {
        super("bottom", "Teleports to the nearest safe lower location");
        this.backManager = backManager;
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addUsageVariant(new BottomOtherCommand());
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
            Messages.noPerm(context, "/bottom");
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.BOTTOM, "/bottom", BYPASS_PERMISSION, world)) {
            return;
        }
        teleportBottom(context, world, playerRef, playerRef);
    }

    private void teleportBottom(@Nonnull CommandContext context,
                                @Nonnull World fallbackWorld,
                                @Nonnull PlayerRef actor,
                                @Nonnull PlayerRef target) {
        boolean self = actor.getUuid().equals(target.getUuid());
        if (!self && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHER_PERMISSION)) {
            Messages.noPerm(context, "/bottom <player>");
            return;
        }
        World world = resolveTargetWorld(target, fallbackWorld);
        Transform transform = target.getTransform();
        if (world == null || transform == null || transform.getPosition() == null) {
            Messages.errKey(context, "error.target_position_unavailable", Map.of());
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3d safe = findSafeLower(world, (int) Math.floor(pos.x()), (int) Math.floor(pos.y()), (int) Math.floor(pos.z()));
        if (safe == null) {
            Messages.errKey(context, "bottom.no_safe_spot", Map.of());
            return;
        }
        Ref<EntityStore> targetRef = target.getReference();
        Store<EntityStore> targetStore = targetRef != null ? targetRef.getStore() : null;
        if (targetRef == null || targetStore == null) {
            Messages.errKey(context, "error.target_access", Map.of());
            return;
        }
        var rot = transform.getRotation();
        float yaw = rot != null ? rot.y() : 0f;
        float pitch = rot != null ? rot.x() : 0f;
        backManager.recordLocation(target.getUuid(), world.getName(), pos.x(), pos.y(), pos.z(), yaw, pitch);
        if (!cooldowns.apply(actor, CooldownKeys.BOTTOM)) {
            return;
        }
        String err = TeleportationUtil.teleportToLocation(targetStore, targetRef, world.getName(), safe.x(), safe.y(), safe.z(), yaw, pitch);
        if (err != null) {
            Messages.err(context, err);
            return;
        }
        if (self) {
            Messages.okKey(context, "bottom.self", Map.of());
        } else {
            Messages.okKey(context, "bottom.other", Map.of("player", target.getUsername()));
            Messages.sendPrefixedKey(target, "bottom.by", Map.of("player", actor.getUsername()));
        }
    }

    @Nullable
    private Vector3d findSafeLower(@Nonnull World world, int x, int startY, int z) {
        for (int y = Math.min(startY - 1, 318); y >= 1; y--) {
            if (isSolidBlock(world, x, y - 1, z) && !isSolidBlock(world, x, y, z) && !isSolidBlock(world, x, y + 1, z)) {
                return new Vector3d(x + 0.5D, y, z + 0.5D);
            }
        }
        return null;
    }

    private boolean isSolidBlock(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y > 319) return false;
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return false;
        BlockType type = chunk.getBlockType(x, y, z);
        return type != null && type.getMaterial() == BlockMaterial.Solid;
    }

    @Nullable
    private World resolveTargetWorld(@Nonnull PlayerRef target, @Nonnull World fallback) {
        if (target.getWorldUuid() != null) {
            World byUuid = Universe.get().getWorld(target.getWorldUuid());
            if (byUuid != null) return byUuid;
        }
        return fallback;
    }

    private final class BottomOtherCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> targetArg;

        private BottomOtherCommand() {
            super("Teleport another player to the nearest safe lower location");
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            teleportBottom(context, world, playerRef, target);
        }
    }
}
