package xyz.thelegacyvoyage.hyessentialsx.commands.spawn;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
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
import java.util.Map;

public final class SpawnCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.spawn";
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
            Messages.noPerm(context, "/spawn");
            return;
        }
        if (!configManager.isSpawnEnabled()) {
            Messages.errKey(context, "spawn.disabled", Map.of());
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.SPAWN, "/spawn", BYPASS_PERMISSION)) {
            return;
        }

        SpawnModel spawn = spawnManager.getSpawn();
        if (spawn == null && configManager.isUseWorldDefaultSpawnIfUnset()) {
            spawn = spawnManager.getSpawnOrWorldDefault(world, playerRef.getUuid());
        }
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
            Vector3f rot = transform.getRotation();
            startYaw = (rot != null) ? rot.getY() : 0f;
            startPitch = (rot != null) ? rot.getX() : 0f;
        }

        final SpawnModel finalSpawn = spawn;
        int warmupSeconds = configManager.getSpawnWarmupSeconds();
        if (warmupSeconds > 0) {
            if (tpManager.hasPending(playerRef.getUuid())) {
                Messages.errKey(context, "teleport.pending", Map.of());
                return;
            }
            if (startPos == null) {
                Messages.errKey(context, "teleport.position_unavailable", Map.of());
                return;
            }
            Vector3d finalStartPos = startPos.clone();
            float finalStartYaw = startYaw;
            float finalStartPitch = startPitch;
            tpManager.queue(
                    playerRef.getUuid(),
                    finalStartPos,
                    warmupSeconds,
                    buffer -> {
                        String err = TeleportationUtil.teleportToSpawn(buffer, ref, finalSpawn);
                        if (err != null) {
                            Messages.sendPrefixed(playerRef, err);
                            return;
                        }
                        backManager.recordLocation(
                                playerRef.getUuid(),
                                world.getName(),
                                finalStartPos.getX(), finalStartPos.getY(), finalStartPos.getZ(),
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
                    startPos.getX(), startPos.getY(), startPos.getZ(),
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
}



