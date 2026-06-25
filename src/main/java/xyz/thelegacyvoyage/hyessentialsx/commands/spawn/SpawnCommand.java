package xyz.thelegacyvoyage.hyessentialsx.commands.spawn;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3f;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TeleportationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public final class SpawnCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.spawn";
    private static final String OTHER_PERMISSION = "hyessentialsx.spawn.other";
    private static final String ALL_PERMISSION = "hyessentialsx.spawn.all";
    private static final String BYPASS_PERMISSION = "hyessentialsx.spawn.bypass";
    private final SpawnManager spawnManager;
    private final BackManager backManager;
    private final TPManager tpManager;
    private final ConfigManager configManager;
    private final CommandCooldownManager cooldowns;

    public SpawnCommand(@Nonnull SpawnManager spawnManager,
                        @Nonnull BackManager backManager,
                        @Nonnull TPManager tpManager,
                        @Nonnull ConfigManager configManager,
                        @Nonnull CommandCooldownManager cooldowns) {
        super("spawn", "Teleport to the server spawn");
        this.spawnManager = spawnManager;
        this.backManager = backManager;
        this.tpManager = tpManager;
        this.configManager = configManager;
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        this.addUsageVariant(new SpawnOtherCommand());
        this.addSubCommand(new SpawnAllCommand());
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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/spawn");
            return;
        }
        if (!configManager.isSpawnEnabled()) {
            Messages.errKey(context, "spawn.disabled", Map.of());
            return;
        }
        spawnSelf(context, store, ref, playerRef, world);
    }

    private void spawnTarget(@Nonnull CommandContext context,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> ref,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull World world,
                             PlayerRef target) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/spawn");
            return;
        }
        if (!configManager.isSpawnEnabled()) {
            Messages.errKey(context, "spawn.disabled", Map.of());
            return;
        }
        if (target == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }
        if (target.getUuid().equals(playerRef.getUuid())) {
            spawnSelf(context, store, ref, playerRef, world);
            return;
        }
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), OTHER_PERMISSION)) {
            Messages.noPerm(context, "/spawn <player>");
            return;
        }
        SpawnResult result = spawnOther(context, target, world, true);
        if (result == SpawnResult.IMMEDIATE) {
            Messages.okKey(context, "spawn.other.success", Map.of("player", target.getUsername()));
        } else if (result == SpawnResult.QUEUED) {
            Messages.okKey(context, "spawn.other.warmup", Map.of("player", target.getUsername()));
        }
    }

    private void spawnSelf(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.SPAWN, "/spawn", BYPASS_PERMISSION, world)) {
            return;
        }

        SpawnModel spawn = spawnManager.getSpawnForPlayer(world, playerRef);
        if (spawn == null) {
            Messages.errKey(context, "spawn.not_set", Map.of());
            return;
        }

        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
        Vector3d startPos = null;
        float startYaw = 0f;
        float startPitch = 0f;
        if (transform != null) {
            startPos = transform.getPosition();
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            startYaw = (rot != null) ? rot.y() : 0f;
            startPitch = (rot != null) ? rot.x() : 0f;
        }

        final SpawnModel finalSpawn = spawn;
        int warmupSeconds = cooldowns.getEffectiveWarmupSeconds(context.sender(), playerRef, CooldownKeys.SPAWN, BYPASS_PERMISSION);
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(playerRef.getUuid())) {
                Messages.errKey(context, "teleport.pending", Map.of());
                return;
            }
            if (startPos == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }
            Vector3d finalStartPos = new org.joml.Vector3d(startPos);
            float finalStartYaw = startYaw;
            float finalStartPitch = startPitch;
            tpManager.queue(
                    playerRef.getUuid(),
                    finalStartPos,
                    warmupSeconds,
                    cooldowns.shouldCancelWarmupOnMove(CooldownKeys.SPAWN),
                    buffer -> {
                        String err = TeleportationUtil.teleportToSpawn(buffer, ref, finalSpawn);
                        if (err != null) {
                            Messages.sendPrefixed(playerRef, err);
                            return;
                        }
                        backManager.recordLocation(
                                playerRef.getUuid(),
                                world.getName(),
                                finalStartPos.x(), finalStartPos.y(), finalStartPos.z(),
                                finalStartYaw, finalStartPitch
                        );
                        cooldowns.apply(playerRef, CooldownKeys.SPAWN);
                        Messages.sendPrefixedKey(playerRef, "teleport.success.spawn", Map.of());
                    }
            );
            Messages.sendPrefixedKey(playerRef, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return;
        }

        if (startPos != null) {
            backManager.recordLocation(
                    playerRef.getUuid(),
                    world.getName(),
                    startPos.x(), startPos.y(), startPos.z(),
                    startYaw, startPitch
            );
        }

        tpManager.cancel(playerRef.getUuid(), null);
        String err = TeleportationUtil.teleportToSpawn(store, ref, spawn);
        if (err != null) {
            Messages.err(context, err);
            return;
        }

        cooldowns.apply(playerRef, CooldownKeys.SPAWN);
        Messages.okKey(context, "teleport.success.spawn", Map.of());
    }

    private SpawnResult spawnOther(@Nonnull CommandContext context,
                                   @Nonnull PlayerRef target,
                                   @Nonnull World fallbackWorld,
                                   boolean notifySender) {
        World targetWorld = resolveTargetWorld(target, fallbackWorld);
        if (targetWorld == null) {
            if (notifySender) {
                Messages.errKey(context, "error.world_not_loaded", Map.of());
            }
            return SpawnResult.FAILED;
        }

        SpawnModel spawn = spawnManager.getSpawnForPlayer(targetWorld, target);
        if (spawn == null) {
            if (notifySender) {
                Messages.errKey(context, "spawn.not_set", Map.of());
            }
            return SpawnResult.FAILED;
        }

        if (!cooldowns.canUse(target, CooldownKeys.SPAWN, "/spawn", BYPASS_PERMISSION, targetWorld)) {
            if (notifySender) {
                Messages.errKey(context, "error.target_cooldown", Map.of());
            }
            return SpawnResult.FAILED;
        }

        com.hypixel.hytale.math.vector.Transform transform = target.getTransform();
        Vector3d startPos = null;
        float startYaw = 0f;
        float startPitch = 0f;
        if (transform != null) {
            startPos = transform.getPosition();
            com.hypixel.hytale.math.vector.Rotation3f rot = transform.getRotation();
            startYaw = (rot != null) ? rot.y() : 0f;
            startPitch = (rot != null) ? rot.x() : 0f;
        }

        Ref<EntityStore> targetRef = target.getReference();
        Store<EntityStore> targetStore = targetRef != null ? targetRef.getStore() : null;
        if (targetRef == null || targetStore == null) {
            if (notifySender) {
                Messages.errKey(context, "error.target_access", Map.of());
            }
            return SpawnResult.FAILED;
        }

        final SpawnModel finalSpawn = spawn;
        int warmupSeconds = cooldowns.getEffectiveWarmupSeconds(context.sender(), target, CooldownKeys.SPAWN, BYPASS_PERMISSION);
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(target.getUuid())) {
                if (notifySender) {
                    Messages.errKey(context, "error.target_pending_teleport", Map.of());
                }
                return SpawnResult.FAILED;
            }
            if (startPos == null) {
                if (notifySender) {
                    Messages.errKey(context, "error.target_position_unavailable", Map.of());
                }
                return SpawnResult.FAILED;
            }
            Vector3d finalStartPos = new org.joml.Vector3d(startPos);
            float finalStartYaw = startYaw;
            float finalStartPitch = startPitch;
            tpManager.queue(
                    target.getUuid(),
                    finalStartPos,
                    warmupSeconds,
                    cooldowns.shouldCancelWarmupOnMove(CooldownKeys.SPAWN),
                    buffer -> {
                        String err = TeleportationUtil.teleportToSpawn(buffer, targetRef, finalSpawn);
                        if (err != null) {
                            Messages.sendPrefixed(target, err);
                            return;
                        }
                        backManager.recordLocation(
                                target.getUuid(),
                                targetWorld.getName(),
                                finalStartPos.x(), finalStartPos.y(), finalStartPos.z(),
                                finalStartYaw, finalStartPitch
                        );
                        cooldowns.apply(target, CooldownKeys.SPAWN);
                        Messages.sendPrefixedKey(target, "teleport.success.spawn", Map.of());
                    }
            );
            Messages.sendPrefixedKey(target, "teleport.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));
            return SpawnResult.QUEUED;
        }

        if (startPos != null) {
            backManager.recordLocation(
                    target.getUuid(),
                    targetWorld.getName(),
                    startPos.x(), startPos.y(), startPos.z(),
                    startYaw, startPitch
            );
        }

        tpManager.cancel(target.getUuid(), null);
        String err = TeleportationUtil.teleportToSpawn(targetStore, targetRef, spawn);
        if (err != null) {
            if (notifySender) {
                Messages.err(context, err);
            }
            return SpawnResult.FAILED;
        }

        cooldowns.apply(target, CooldownKeys.SPAWN);
        Messages.sendPrefixedKey(target, "teleport.success.spawn", Map.of());
        return SpawnResult.IMMEDIATE;
    }

    @Nullable
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

    private enum SpawnResult {
        IMMEDIATE,
        QUEUED,
        FAILED
    }

    private final class SpawnOtherCommand extends AbstractPlayerCommand {

        private final RequiredArg<PlayerRef> targetArg;

        private SpawnOtherCommand() {
            super("Teleport a player to spawn");
            this.setPermissionGroups();
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            spawnTarget(context, store, ref, playerRef, world, context.get(targetArg));
        }
    }

    private final class SpawnAllCommand extends AbstractPlayerCommand {

        private SpawnAllCommand() {
            super("all", "Teleport all players to spawn");
            this.setPermissionGroups();
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
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
                Messages.noPerm(context, "/spawn");
                return;
            }
            if (!configManager.isSpawnEnabled()) {
                Messages.errKey(context, "spawn.disabled", Map.of());
                return;
            }
            if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), ALL_PERMISSION)) {
                Messages.noPerm(context, "/spawn all");
                return;
            }
            int success = 0;
            int skipped = 0;
            for (PlayerRef target : Universe.get().getPlayers()) {
                if (target == null) continue;
                SpawnResult result = spawnOther(context, target, world, false);
                if (result == SpawnResult.IMMEDIATE || result == SpawnResult.QUEUED) {
                    success++;
                } else {
                    skipped++;
                }
            }
            String msg = Messages.tr(null, "spawn.all.success", Map.of(
                    "count", String.valueOf(success)
            ));
            if (skipped > 0) {
                msg += " " + Messages.tr(null, "spawn.all.skipped", Map.of(
                        "count", String.valueOf(skipped)
                ));
            }
            Messages.ok(context, msg);
        }
    }
}




