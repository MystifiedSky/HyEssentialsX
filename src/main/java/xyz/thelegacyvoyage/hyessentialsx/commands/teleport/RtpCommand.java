package xyz.thelegacyvoyage.hyessentialsx.commands.teleport;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.collision.WorldUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public final class RtpCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.rtp";
    private static final String BYPASS_PERMISSION = "hyessentialsx.rtp.bypass";
    private static final String OTHER_PERMISSION = "hyessentialsx.rtp.other";
    private static final int MAX_ATTEMPTS = 20;
    private static final int MIN_WORLD_Y = 0;
    private static final int MAX_WORLD_Y = 319;

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
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/rtp");
            return;
        }
        if (!config.isRtpEnabled()) {
            Messages.errKey(context, "rtp.disabled", Map.of());
            return;
        }
        PlayerRef senderPlayer = CommandSenderUtil.resolvePlayer(context);
        List<String> args = CommandInputUtil.getArgs(context);
        PlayerRef target = senderPlayer;
        String worldArg = null;
        if (args.isEmpty()) {
            if (senderPlayer == null) {
                Messages.errKey(context, "rtp.usage", Map.of());
                return;
            }
        } else {
            String targetName = args.get(0);
            PlayerRef resolved = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
            if (resolved == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            target = resolved;
            if (args.size() >= 2) {
                worldArg = args.get(1);
            }
        }
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }
        boolean isOther = senderPlayer == null || !target.getUuid().equals(senderPlayer.getUuid());
        if (isOther && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHER_PERMISSION)) {
            Messages.noPerm(context, "/rtp <player>");
            return;
        }
        if (!isOther) {
            if (!cooldowns.canUse(context, senderPlayer, CooldownKeys.RTP, "/rtp", BYPASS_PERMISSION)) {
                return;
            }
        } else {
            if (!cooldowns.canUse(target, CooldownKeys.RTP, "/rtp", BYPASS_PERMISSION)) {
                Messages.errKey(context, "error.target_cooldown", Map.of());
                return;
            }
        }

        if (tpManager.hasPending(target.getUuid())) {
            Messages.errKey(context, "teleport.pending", Map.of());
            return;
        }

        World senderWorld = null;
        if (senderPlayer != null && senderPlayer.getWorldUuid() != null) {
            senderWorld = Universe.get().getWorld(senderPlayer.getWorldUuid());
        }
        World chosenWorld = resolveTargetWorld(target, senderWorld);
        if (chosenWorld == null) {
            Messages.errKey(context, "error.world_not_loaded", Map.of());
            return;
        }
        if (worldArg != null && !worldArg.isBlank()) {
            World specified = Universe.get().getWorld(worldArg);
            if (specified == null) {
                Messages.errKey(context, "error.world_not_loaded_named", Map.of("world", worldArg));
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
                Messages.warnKey(context, "rtp.override_world_missing", Map.of("world", overrideWorldName));
            }
        }
        final World targetWorld = chosenWorld;

        Transform transform = target.getTransform();
        if (transform == null) {
            Messages.errKey(context, "teleport.position_unavailable", Map.of());
            return;
        }

        Vector3d pos = transform.getPosition();
        if (pos == null) {
            Messages.errKey(context, "teleport.position_unavailable", Map.of());
            return;
        }
        int minDist = Math.max(0, config.getRtpMinDistance());
        int maxDist = Math.max(10, config.getRtpMaxDistance());
        if (minDist > maxDist) {
            minDist = maxDist;
        }
        Vector3d searchOrigin = pos.clone();
        int warmupSeconds = config.getRtpWarmupSeconds();
        if (cooldowns.hasWarmupBypass(context, target, CooldownKeys.RTP, BYPASS_PERMISSION)) {
            warmupSeconds = 0;
        }
        final int finalWarmupSeconds = warmupSeconds;
        PlayerRef finalTarget = target;
        boolean finalIsOther = isOther;
        int finalMinDist = minDist;
        int finalMaxDist = maxDist;
        runOnWorldThread(targetWorld, () -> findRandomSafeLocationAsync(
                targetWorld,
                searchOrigin,
                finalMinDist,
                finalMaxDist,
                MAX_ATTEMPTS,
                0,
                destination -> onSafeDestinationFound(
                        context,
                        finalTarget,
                        targetWorld,
                        destination,
                        finalWarmupSeconds,
                        finalIsOther
                )
        ));
    }

    private void onSafeDestinationFound(@Nonnull CommandContext context,
                                        @Nonnull PlayerRef target,
                                        @Nonnull World targetWorld,
                                        @Nullable Vector3d destination,
                                        int warmupSeconds,
                                        boolean isOther) {
        if (destination == null) {
            Messages.errKey(context, "rtp.no_safe_location", Map.of());
            return;
        }

        if (warmupSeconds > 0) {
            Transform transformNow = target.getTransform();
            if (transformNow == null || transformNow.getPosition() == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }
            Ref<EntityStore> warmupRef = target.getReference();
            if (warmupRef == null || !warmupRef.isValid()) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            com.hypixel.hytale.math.vector.Vector3f rot = transformNow.getRotation();
            float startYaw = (rot != null) ? rot.getY() : 0f;
            float startPitch = (rot != null) ? rot.getX() : 0f;
            Vector3d startPos = transformNow.getPosition().clone();
            double targetX = destination.getX();
            double targetY = destination.getY();
            double targetZ = destination.getZ();
            tpManager.queue(
                    target.getUuid(),
                    startPos,
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToLocation(
                                buffer,
                                warmupRef,
                                targetWorld.getName(),
                                targetX, targetY, targetZ,
                                0f, 0f
                        );
                        if (err != null) {
                            Messages.sendPrefixed(target, err);
                            return;
                        }
                        backManager.recordLocation(
                                target.getUuid(),
                                targetWorld.getName(),
                                startPos.getX(), startPos.getY(), startPos.getZ(),
                                startYaw, startPitch
                        );
                        cooldowns.apply(target, CooldownKeys.RTP);
                        Messages.sendPrefixedKey(target, "teleport.success.rtp", Map.of());
                    }
            );
            Messages.sendPrefixedKey(target, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            if (isOther) {
                Messages.okKey(context, "rtp.other.warmup", Map.of("player", target.getUsername()));
            }
            return;
        }

        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef == null || !targetRef.isValid()) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }
        Store<EntityStore> targetStore = targetRef.getStore();
        if (targetStore == null || targetStore.getExternalData() == null || targetStore.getExternalData().getWorld() == null) {
            Messages.errKey(context, "error.world_not_loaded", Map.of());
            return;
        }

        double targetX = destination.getX();
        double targetY = destination.getY();
        double targetZ = destination.getZ();
        targetStore.getExternalData().getWorld().execute(() -> {
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
                    targetStore,
                    targetRef,
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
                Messages.okKey(context, "rtp.other.success", Map.of("player", target.getUsername()));
            } else {
                Messages.okKey(context, "teleport.success.rtp", Map.of());
            }
        });
    }

    @Nullable
    private World resolveTargetWorld(@Nonnull PlayerRef target, @Nullable World fallback) {
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

    private void findRandomSafeLocationAsync(@Nonnull World world,
                                             @Nonnull Vector3d center,
                                             int minRadius,
                                             int maxRadius,
                                             int maxChecks,
                                             int currentAttempt,
                                             @Nonnull Consumer<Vector3d> callback) {
        if (currentAttempt >= maxChecks) {
            callback.accept(null);
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
        int randomX = (int) (center.getX() + distance * Math.cos(angle));
        int randomZ = (int) (center.getZ() + distance * Math.sin(angle));
        long chunkIndex = ChunkUtil.indexChunkFromBlock(randomX, randomZ);
        WorldChunk existingChunk = world.getChunkIfLoaded(chunkIndex);
        if (existingChunk == null) {
            existingChunk = world.getChunkIfInMemory(chunkIndex);
        }

        if (existingChunk != null) {
            Vector3d safePosition = findHighestSolidBlock(world, randomX, randomZ);
            if (safePosition != null) {
                callback.accept(safePosition);
            } else {
                findRandomSafeLocationAsync(world, center, minRadius, maxRadius, maxChecks, currentAttempt + 1, callback);
            }
            return;
        }

        world.getChunkAsync(chunkIndex).whenComplete((chunk, error) ->
                runOnWorldThread(world, () -> {
                    if (error == null && chunk != null) {
                        Vector3d safePosition = findHighestSolidBlock(world, randomX, randomZ);
                        if (safePosition != null) {
                            callback.accept(safePosition);
                        } else {
                            findRandomSafeLocationAsync(world, center, minRadius, maxRadius, maxChecks, currentAttempt + 1, callback);
                        }
                    } else {
                        findRandomSafeLocationAsync(world, center, minRadius, maxRadius, maxChecks, currentAttempt + 1, callback);
                    }
                })
        );
    }

    @Nullable
    private Vector3d findHighestSolidBlock(@Nonnull World world, int x, int z) {
        for (int y = MAX_WORLD_Y; y >= MIN_WORLD_Y; y--) {
            if (isWaterOrLava(world, x, y, z)) {
                return null;
            }
            if (isSolidBlock(world, x, y, z)
                    && !isSolidBlock(world, x, y + 1, z)
                    && !isSolidBlock(world, x, y + 2, z)
                    && isSafeGround(world, x, y, z)) {
                return new Vector3d(x + 0.5, y + 1.0, z + 0.5);
            }
        }
        return null;
    }

    private boolean isSolidBlock(@Nonnull World world, int x, int y, int z) {
        if (!isValidY(y)) {
            return false;
        }
        BlockType type = getBlockType(world, x, y, z);
        return type != null && type.getMaterial() == BlockMaterial.Solid;
    }

    private boolean isSafeGround(@Nonnull World world, int x, int y, int z) {
        return isSolidBlock(world, x, y, z);
    }

    private boolean isWaterOrLava(@Nonnull World world, int x, int y, int z) {
        if (!isValidY(y)) {
            return false;
        }
        int fluidId = getBlockFluidId(world, x, y, z);
        return fluidId == 7 || fluidId == 8 || fluidId == 6 || fluidId == 11;
    }

    private int getBlockFluidId(@Nonnull World world, int x, int y, int z) {
        if (!isValidY(y)) {
            return 0;
        }
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk worldChunk = world.getChunk(chunkIndex);
            if (worldChunk == null) {
                return 0;
            }
            Ref<ChunkStore> chunkRef = worldChunk.getReference();
            if (chunkRef == null) {
                return 0;
            }
            ChunkColumn chunkColumn = world.getChunkStore().getStore().getComponent(chunkRef, ChunkColumn.getComponentType());
            if (chunkColumn == null) {
                return 0;
            }
            return WorldUtil.getFluidIdAtPosition(world.getChunkStore().getStore(), chunkColumn, x, y, z);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    @Nullable
    private BlockType getBlockType(@Nonnull World world, int x, int y, int z) {
        if (!isValidY(y)) {
            return null;
        }
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk worldChunk = world.getChunk(chunkIndex);
            if (worldChunk == null) {
                return null;
            }
            return worldChunk.getBlockType(x, y, z);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isValidY(int y) {
        return y >= MIN_WORLD_Y && y <= MAX_WORLD_Y;
    }

    private void runOnWorldThread(@Nonnull World world, @Nonnull Runnable task) {
        if (world.isInThread()) {
            task.run();
            return;
        }
        try {
            world.execute(task);
        } catch (IllegalThreadStateException ignored) {
        }
    }
}




