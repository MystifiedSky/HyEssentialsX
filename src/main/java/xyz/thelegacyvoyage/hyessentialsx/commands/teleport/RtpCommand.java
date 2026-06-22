package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RtpCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.rtp";
    private static final String BYPASS_PERMISSION = "hyessentialsx.rtp.bypass";
    private static final String OTHER_PERMISSION = "hyessentialsx.rtp.other";

    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final TPManager tpManager;
    private final BackManager backManager;
    private final Random random = new Random();

    public RtpCommand(@Nonnull ConfigManager config,
                      @Nonnull CommandCooldownManager cooldowns,
                      @Nonnull TPManager tpManager,
                      @Nonnull BackManager backManager) {
        super("rtp", "Random teleport");
        this.config = config;
        this.cooldowns = cooldowns;
        this.tpManager = tpManager;
        this.backManager = backManager;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
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
            Messages.errKey(context, "rtp.disabled", Map.of());
            return;
        }
        List<String> args = CommandInputUtil.getArgs(context);
        PlayerRef target = playerRef;
        String worldArg = null;
        boolean isOther = false;
        if (!args.isEmpty()) {
            String targetName = args.get(0);
            PlayerRef resolved = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
            if (resolved == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            target = resolved;
            isOther = !target.getUuid().equals(playerRef.getUuid());
            if (isOther && !context.sender().hasPermission(OTHER_PERMISSION)) {
                Messages.noPerm(context, "/rtp <player>");
                return;
            }
            if (args.size() >= 2) {
                worldArg = args.get(1);
            }
        }
        if (!isOther) {
            if (!cooldowns.canUse(context, playerRef, CooldownKeys.RTP, "/rtp", BYPASS_PERMISSION)) {
                return;
            }
        } else {
            if (!cooldowns.canUse(target, CooldownKeys.RTP, "/rtp", BYPASS_PERMISSION)) {
                Messages.err(context, "Target is on cooldown.");
                return;
            }
        }

        if (tpManager.hasPending(target.getUuid())) {
            Messages.errKey(context, "teleport.pending", Map.of());
            return;
        }

        World chosenWorld = resolveTargetWorld(target, world);
        if (worldArg != null && !worldArg.isBlank()) {
            World specified = Universe.get().getWorld(worldArg);
            if (specified == null) {
                Messages.err(context, "World '" + worldArg + "' is not loaded.");
                return;
            }
            chosenWorld = specified;
        }
        String overrideWorldName = config.getRtpWorldOverride(chosenWorld.getName());
        if (overrideWorldName != null && !overrideWorldName.equalsIgnoreCase(chosenWorld.getName())) {
            World overrideWorld = Universe.get().getWorld(overrideWorldName);
            if (overrideWorld != null) {
                chosenWorld = overrideWorld;
            } else {
                Messages.warn(context, "RTP override world '" + overrideWorldName + "' is not loaded. Using current world.");
            }
        }
        final World targetWorld = chosenWorld;

        Transform transform = target.getTransform();
        if (transform == null) {
            Messages.errKey(context, "teleport.position_unavailable", Map.of());
            return;
        }

        Vector3d pos = transform.getPosition();
        int minDist = Math.max(0, config.getRtpMinDistance());
        int maxDist = Math.max(10, config.getRtpMaxDistance());
        if (minDist > maxDist) {
            minDist = maxDist;
        }

        Double targetX = null;
        Double targetZ = null;
        Double targetY = null;

        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = minDist + (random.nextDouble() * (maxDist - minDist));
            double dx = Math.cos(angle) * distance;
            double dz = Math.sin(angle) * distance;
            int blockX = (int) Math.floor(pos.getX() + dx);
            int blockZ = (int) Math.floor(pos.getZ() + dz);
            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
            WorldChunk chunk = targetWorld.getChunk(chunkIndex);
            if (chunk == null) continue;

            int localX = ChunkUtil.localCoordinate(blockX);
            int localZ = ChunkUtil.localCoordinate(blockZ);
            short height = chunk.getHeight(localX, localZ);
            if (isLiquidAt(chunk, blockX, height, blockZ)) {
                continue;
            }

            for (int y = height; y >= 1; y--) {
                if (!isSolidBlock(chunk, blockX, y, blockZ)) {
                    continue;
                }
                if (!isSafeAbove(chunk, blockX, y, blockZ)) {
                    continue;
                }
                targetX = blockX + 0.5;
                targetZ = blockZ + 0.5;
                targetY = y + 1.0;
                break;
            }
            if (targetX != null) {
                break;
            }
        }

        if (targetX == null || targetY == null || targetZ == null) {
            Messages.errKey(context, "rtp.no_safe_location", Map.of());
            return;
        }

        int warmupSeconds = config.getRtpWarmupSeconds();
        if (warmupSeconds > 0) {
            Transform transformNow = target.getTransform();
            if (transformNow == null || transformNow.getPosition() == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }
            PlayerRef warmupTarget = target;
            Ref<EntityStore> warmupRef = warmupTarget.getReference();
            com.hypixel.hytale.math.vector.Vector3f rot = transformNow.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            Vector3d startPos = transformNow.getPosition().clone();
            double finalTargetX = targetX;
            double finalTargetY = targetY;
            double finalTargetZ = targetZ;
            tpManager.queue(
                    warmupTarget.getUuid(),
                    startPos,
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                warmupRef,
                                targetWorld.getName(),
                                finalTargetX, finalTargetY, finalTargetZ,
                                0f, 0f
                        );
                        if (err != null) {
                            Messages.sendPrefixed(warmupTarget, err);
                            return;
                        }
                        backManager.recordLocation(
                                warmupTarget.getUuid(),
                                targetWorld.getName(),
                                startPos.getX(), startPos.getY(), startPos.getZ(),
                                startYaw, startPitch
                        );
                        cooldowns.apply(warmupTarget, CooldownKeys.RTP);
                        Messages.sendPrefixedKey(warmupTarget, "teleport.success.rtp", Map.of());
                    }
            );
            Messages.sendPrefixedKey(warmupTarget, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            if (isOther) {
                Messages.ok(context, "Teleporting " + target.getUsername() + " shortly...");
            }
            return;
        }

        Transform transformNow = target.getTransform();
        if (transformNow != null && transformNow.getPosition() != null) {
            com.hypixel.hytale.math.vector.Vector3f rot = transformNow.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            backManager.recordLocation(
                    target.getUuid(),
                    targetWorld.getName(),
                    transformNow.getPosition().getX(),
                    transformNow.getPosition().getY(),
                    transformNow.getPosition().getZ(),
                    startYaw, startPitch
            );
        }

        String err = TeleportationUtil.teleportToLocation(
                target.getReference().getStore(),
                target.getReference(),
                targetWorld.getName(),
                targetX, targetY, targetZ,
                0f, 0f
        );
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        cooldowns.apply(target, CooldownKeys.RTP);
        Messages.sendPrefixedKey(target, "teleport.success.rtp", Map.of());
        if (isOther) {
            Messages.ok(context, "Teleported " + target.getUsername() + " to a random location.");
        } else {
            Messages.okKey(context, "teleport.success.rtp", Map.of());
        }
    }

    @Nonnull
    private World resolveTargetWorld(@Nonnull PlayerRef target, @Nonnull World fallback) {
        try {
            Ref<EntityStore> targetRef = target.getReference();
            Store<EntityStore> targetStore = targetRef.getStore();
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

    private boolean isSolidBlock(@Nonnull WorldChunk chunk, int x, int y, int z) {
        BlockType type = chunk.getBlockType(x, y, z);
        return type != null && type.getMaterial() == BlockMaterial.Solid;
    }

    private boolean isSafeAbove(@Nonnull WorldChunk chunk, int x, int y, int z) {
        return isClearBlock(chunk, x, y + 1, z) && isClearBlock(chunk, x, y + 2, z);
    }

    private boolean isClearBlock(@Nonnull WorldChunk chunk, int x, int y, int z) {
        BlockType type = chunk.getBlockType(x, y, z);
        if (type != null && type.getMaterial() == BlockMaterial.Solid) {
            return false;
        }
        return !isLiquidAt(chunk, x, y, z) && !isLiquidBlock(type);
    }

    private boolean isLiquidAt(@Nonnull WorldChunk chunk, int x, int y, int z) {
        try {
            return chunk.getFluidLevel(x, y, z) > 0 || chunk.getFluidId(x, y, z) != 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isLiquidBlock(BlockType type) {
        if (type == null) {
            return false;
        }
        String id = type.getId();
        String group = type.getGroup();
        StringBuilder combined = new StringBuilder();
        if (id != null) {
            combined.append(id);
        }
        if (group != null) {
            combined.append(' ').append(group);
        }
        String lowered = combined.toString().toLowerCase();
        return lowered.contains("water") || lowered.contains("lava") || lowered.contains("fluid");
    }
}



